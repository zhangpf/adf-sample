package NUDT.tactics.utils;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.StandardMessage;
import adf.agent.communication.standard.bundle.StandardMessagePriority;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.information.MessageBuilding;
import adf.agent.communication.standard.bundle.information.MessageCivilian;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.communication.CommunicationMessage;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class MessageTool
{
    private DevelopData developData;

    /**
     * agent会把自己对世界的“新”的认知通过消息发送出去，这里新的认知仅指自己观察到的，也即worldInfo中的ChangeSet
     * 例，A在t1时刻发生变化，并通知其他人自己发生变化，B在t2收到A在t1时刻发生变化的消息，而B又在t3时刻亲自观察到了A发生的变化
     * 如果t3 - t2 < sendingAvoidTimeReceived，也即观察到的变化实际上已经包含在最近收到的消息中了，则不再发送这个认知
     */
    private int sendingAvoidTimeReceived;
    private int sendingAvoidTimeSent;
    
    /**
     * 在同一个Area上最多停留几个周期
     */
    private int sendingAvoidTimeClearRequest;
    
    /**
     * 每个周期移动距离的估计值
     */
    private int estimatedMoveDistance;

    private int maxTimeStep = Integer.MAX_VALUE;
    private Map<EntityID, Integer> prevBrokenessMap;
    
    /**
     * 记录上一周期所在的Area
     */
    private EntityID lastPosition;
    private int lastSentTime;
    /**
     * 记录在lastPosition所指的Entity停留了多少个周期
     */
    private int stayCount;

    /**
     * 记录Entity在什么时刻发生变化（这些变化信息是从消息中解析出来的）
     */
    private Map<EntityID, Integer> receivedTimeMap;
    private Set<EntityID> agentsPotition;
    private Set<EntityID> receivedPassableRoads;

    private EntityID dominanceAgentID;

    public MessageTool(ScenarioInfo scenarioInfo, DevelopData developData)
    {
        this.developData = developData;

        this.sendingAvoidTimeReceived = developData.getInteger("sample.tactics.MessageTool.sendingAvoidTimeReceived", 3);
        this.sendingAvoidTimeSent = developData.getInteger("sample.tactics.MessageTool.sendingAvoidTimeSent", 5);
        this.sendingAvoidTimeClearRequest = developData.getInteger("sample.tactics.MessageTool.sendingAvoidTimeClearRequest", 5);
        this.estimatedMoveDistance = developData.getInteger("sample.tactics.MessageTool.estimatedMoveDistance", 40000);

        this.lastPosition = new EntityID(0);
        this.lastSentTime = 0;
        this.stayCount = 0;
        
        /**
         * 记录建筑物损坏情况
         */
        this.prevBrokenessMap = new HashMap<>();
        this.receivedTimeMap = new HashMap<>();
        this.agentsPotition = new HashSet<>();
        /**
         * 根据收到的消息判断出的道路通畅信息
         */
        this.receivedPassableRoads = new HashSet<>();

        this.dominanceAgentID = new EntityID(0);
    }

    public void reflectMessage(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager)
    {
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        changedEntities.add(agentInfo.getID());
        int time = agentInfo.getTime();
        for (CommunicationMessage message : messageManager.getReceivedMessageList(StandardMessage.class))
        {
            StandardEntity entity = null;
            entity = MessageUtil.reflectMessage(worldInfo, (StandardMessage) message);
            if (entity != null) { this.receivedTimeMap.put(entity.getID(), time); }
        }
    }

    /**
     * 将自己观察到的变化通过消息发送出去
     * @param agentInfo
     * @param worldInfo
     * @param scenarioInfo
     * @param messageManager
     */
    public void sendInformationMessages(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager)
    {
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();

        this.updateInfo(agentInfo, worldInfo, scenarioInfo, messageManager);

        if (isPositionMoved(agentInfo) && isDominance(agentInfo))
        {
            for (EntityID entityID : changedEntities)
            {
                if (!(isRecentlyReceived(agentInfo, entityID)))
                {
                    StandardEntity entity = worldInfo.getEntity(entityID);
                    CommunicationMessage message = null;
                    switch (entity.getStandardURN())
                    {
                        case ROAD:
                            Road road = (Road) entity;
                            //静态地图中没有路障，所以只把通畅的道路告知其他agent，判断条件“没有到达”是为了避免重复发送
                            if (isNonBlockadeAndNotReceived(road))
                            {
                                message = new MessageRoad(true, StandardMessagePriority.LOW,
                                        road, null,
                                        true, false);
                            }
                            break;
                        case BUILDING:
                            Building building = (Building) entity;
                            //静态地图中建筑都完好，所以只把着火或者损毁的建筑信息发送出去
                            if (isOnFireOrWaterDameged(building))
                            {
                                message = new MessageBuilding(true, StandardMessagePriority.LOW, building);
                            }
                            break;
                        case CIVILIAN:
                            Civilian civilian = (Civilian) entity;
                            //静态地图中平民都没有受伤，所以只把不能移动的平民信息发送出去
                            if (isUnmovalCivilian(civilian))
                            {
                                message = new MessageCivilian(true, StandardMessagePriority.LOW, civilian);
                            }
                            break;
                    }

                    messageManager.addMessage(message);
                }
            }
        }

        recordLastPosition(agentInfo);
    }

    /**
     * 发送呼救信号“请求警察清理道路”，
     * 发送该消息的判断依据：
     *     1.当前Agent被路障围住
     *     2.当前Agent在同一个Area停留过久
     * @param agentInfo
     * @param worldInfo
     * @param scenarioInfo
     * @param messageManager
     */
    public void sendRequestMessages (AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager)
    {
        if (agentInfo.me().getStandardURN() == AMBULANCE_TEAM
                || agentInfo.me().getStandardURN() == FIRE_BRIGADE)
        {
            int currentTime = agentInfo.getTime();
            Human agent = (Human) agentInfo.me();
            int agentX = agent.getX();
            int agentY = agent.getY();
            StandardEntity positionEntity = worldInfo.getPosition(agent);
            if (positionEntity instanceof Road)
            {
                boolean isSendRequest = false;

                Road road = (Road) positionEntity;
                if (road.isBlockadesDefined() && road.getBlockades().size() > 0)
                {
                    for (Blockade blockade : worldInfo.getBlockades(road))
                    {
                        if (blockade == null || !blockade.isApexesDefined())
                        { continue; }

                        if (this.isInside(agentX, agentY, blockade.getApexes()))
                        { isSendRequest = true; }
                    }
                }

                if (this.lastPosition != null && this.lastPosition.getValue() == road.getID().getValue())
                {
                    this.stayCount++;
                    if (this.stayCount > this.getMaxTravelTime(road))
                    {
                        isSendRequest = true;
                    }
                }
                else
                {
                    this.lastPosition = road.getID();
                    this.stayCount = 0;
                }

                if (isSendRequest && ((currentTime - this.lastSentTime) >= this.sendingAvoidTimeClearRequest))
                {
                    this.lastSentTime = currentTime;
                    messageManager.addMessage(
                            new CommandPolice( true, null, agent.getPosition(), CommandPolice.ACTION_CLEAR )
                    );
                }
            }
        }
    }
    
    
    private void updateInfo(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager)
    {
        if (this.maxTimeStep == Integer.MAX_VALUE)
        {
            try
            {
                this.maxTimeStep = scenarioInfo.getKernelTimesteps();
            }
            catch (NoSuchConfigOptionException e)
            {}
        }

        this.agentsPotition.clear();
        this.dominanceAgentID = agentInfo.getID();

        for (StandardEntity entity : worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE))
        {
            Human human = (Human) entity;
            this.agentsPotition.add(human.getPosition());
            //直接比较AgentID大小代表什么意思？？？？？？？？？？？？？？？
            if (agentInfo.getPosition().equals(human.getPosition())
                    && dominanceAgentID.getValue() < entity.getID().getValue())
            {
                this.dominanceAgentID = entity.getID();
            }
        }

        boolean aftershock = false;
        for (EntityID id : agentInfo.getChanged().getChangedEntities())
        {
            if (this.prevBrokenessMap.containsKey(id) && worldInfo.getEntity(id).getStandardURN().equals(BUILDING))
            {
                Building building = (Building)worldInfo.getEntity(id);
                int brokenness = this.prevBrokenessMap.get(id);
                this.prevBrokenessMap.get(id);
                if (building.isBrokennessDefined())
                {
                    if (building.getBrokenness() > brokenness)
                    {
                        aftershock = true;
                    }
                }
            }
        }
        this.prevBrokenessMap.clear();
        for (EntityID id : agentInfo.getChanged().getChangedEntities())
        {
            if (! worldInfo.getEntity(id).getStandardURN().equals(BUILDING)) { continue; }

            Building building = (Building)worldInfo.getEntity(id);
            if (building.isBrokennessDefined())
            {
                this.prevBrokenessMap.put(id, building.getBrokenness());
            }
        }
        if (aftershock)
        {
            this.receivedPassableRoads.clear();
        }

        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageRoad.class))
        {
            MessageRoad messageRoad = (MessageRoad) message;
            Boolean passable = messageRoad.isPassable();
            if (passable != null && passable)
            {
                this.receivedPassableRoads.add(messageRoad.getRoadID());
            }
        }
    }
    
    /**
     * 判断agent相对上一个周期是否移动到了新的Area
     * @param agentInfo
     * @return
     */
    private boolean isPositionMoved(AgentInfo agentInfo)
    {
        return !(agentInfo.getID().equals(lastPosition));
    }

    private boolean isDominance(AgentInfo agentInfo)
    {
        return agentInfo.getID().equals(this.dominanceAgentID);
    }
    
    private boolean isRecentlyReceived(AgentInfo agentInfo, EntityID id)
    {
        return (this.receivedTimeMap.containsKey(id)
                && ((agentInfo.getTime() - this.receivedTimeMap.get(id)) < this.sendingAvoidTimeReceived));
    }
    
    /**
     * 判断一条道路是否通畅并且没有到达过
     * @param road
     * @return
     */
    private boolean isNonBlockadeAndNotReceived(Road road)
    {
        if ((!road.isBlockadesDefined()) || (road.isBlockadesDefined() && (road.getBlockades().size() <= 0) ))
        {
            if (!(this.receivedPassableRoads.contains(road.getID())))
            {
                return true;
            }
        }

        return false;
    }
    
    /**
     * 判断建筑物是否着火或者有water damage
     * @param building
     * @return
     */
    private boolean isOnFireOrWaterDameged(Building building)
    {
        final List<StandardEntityConstants.Fieryness> ignoreFieryness
                = Arrays.asList(StandardEntityConstants.Fieryness.UNBURNT, StandardEntityConstants.Fieryness.BURNT_OUT);

        if (building.isFierynessDefined() && ignoreFieryness.contains(building.getFierynessEnum()) )
        {
            return false;
        }

        return true;
    }
    
    /**
     * 判断civilian是否能移动
     * @param civilian
     * @return
     */
    private boolean isUnmovalCivilian(Civilian civilian)
    {
        return civilian.isDamageDefined() && (civilian.getDamage() > 0);
    }

    private void recordLastPosition(AgentInfo agentInfo)
    {
        this.lastPosition = agentInfo.getPosition();
    }

    /**
     * 计算点(pX, pY)是否在[(apex[2*i], apex[2*i+1])]组成的凸多边形内部
     * 注：如果apex组成的是凹多边形，判断法则失效
     * @param pX
     * @param pY
     * @param apex
     * @return
     */
    private boolean isInside(double pX, double pY, int[] apex)
    {
        Point2D p = new Point2D(pX, pY);
        Vector2D v1 = (new Point2D(apex[apex.length - 2], apex[apex.length - 1])).minus(p);
        Vector2D v2 = (new Point2D(apex[0], apex[1])).minus(p);
        double theta = this.getAngle(v1, v2);

        for (int i = 0; i < apex.length - 2; i += 2)
        {
            v1 = (new Point2D(apex[i], apex[i + 1])).minus(p);
            v2 = (new Point2D(apex[i + 2], apex[i + 3])).minus(p);
            theta += this.getAngle(v1, v2);
        }
        return Math.round(Math.abs((theta / 2) / Math.PI)) >= 1;
    }
    
    /**
     * 计算两个向量之间的夹角
     * @param v1
     * @param v2
     * @return
     */
    private double getAngle(Vector2D v1, Vector2D v2)
    {
        double flag = (v1.getX() * v2.getY()) - (v1.getY() * v2.getX());
        double angle = Math.acos(((v1.getX() * v2.getX()) + (v1.getY() * v2.getY())) / (v1.getLength() * v2.getLength()));
        if (flag > 0)
        {
            return angle;
        }
        if (flag < 0)
        {
            return -1 * angle;
        }
        return 0.0D;
    }

    /**
     * 计算穿过Area所需的最大时间
     * 枚举能通过的边，依次选两条，连接两边的中点作为通过的路径，
     * 用此路径长度除以每个周期行动距离的估计值，向上取整并+1
     * @param area
     * @return
     */
    private int getMaxTravelTime(Area area)
    {
        int distance = 0;
        List<Edge> edges = new ArrayList<>();
        for (Edge edge : area.getEdges())
        {
            if (edge.isPassable())
            {
                edges.add(edge);
            }
        }
        if (edges.size() <= 1)
        {
            return this.maxTimeStep;
        }
        for (int i = 0; i < edges.size(); i++)
        {
            for (int j = 0; j < edges.size(); j++)
            {
                if (i != j)
                {
                    Edge edge1 = edges.get(i);
                    double midX1 = (edge1.getStartX() + edge1.getEndX()) / 2;
                    double midY1 = (edge1.getStartY() + edge1.getEndY()) / 2;
                    Edge edge2 = edges.get(j);
                    double midX2 = (edge2.getStartX() + edge2.getEndX()) / 2;
                    double midY2 = (edge2.getStartY() + edge2.getEndY()) / 2;
                    int d = this.getDistance(midX1, midY1, midX2, midY2);
                    if (distance < d)
                    {
                        distance = d;
                    }
                }
            }
        }

        if (distance > 0)
        {
            return 1 + (int)Math.ceil( distance / (double)this.estimatedMoveDistance);
        }

        return this.maxTimeStep;
    }

    private int getDistance(double fromX, double fromY, double toX, double toY)
    {
        double dx = toX - fromX;
        double dy = toY - fromY;
        return (int) Math.hypot(dx, dy);
    }
}
