package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.RoadDetector;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 选择要探索的Road
 * 1.根据world中信息构造targetAreas和priorityRoads
 * 2.根据消息修改targetAreas和priorityRoads
 * 3.根据pathPlanning模块从targetAreas或priorityRoads中选出一个要探索的Road
 * @author gxd
 */
public class SampleRoadDetector extends RoadDetector
{
	//需要探索的Areas集合
    private Set<EntityID> targetAreas;
    //需要去的Roads集合（Roads比较重要，如果Roads中有路障，可能导致targetAreas中某些Area实际不可达）
    private Set<EntityID> priorityRoads;

    private PathPlanning pathPlanning;

    private EntityID result;
    
    /**
     * 根据配置信息初始化pathPlanning模块
     * @param ai
     * @param wi
     * @param si
     * @param moduleManager
     * @param developData
     */
    public SampleRoadDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);
        switch (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
        }
        registerModule(this.pathPlanning);
        this.result = null;
    }

    /**
     * 根据pathPlanning模块从this.priorityRoads与this.targetRoads中取出一个road，并将其设置为this.result
     */
    @Override
    public RoadDetector calc()
    {
        if (this.result == null)
        {
        	//得到agent所在Area的EntityID
            EntityID positionID = this.agentInfo.getPosition();
            //如果Agent已经在this.targetAreas中的某一个Area，则Agent已经在目标了
            if (this.targetAreas.contains(positionID))
            {
                this.result = positionID;
                return this;
            }
            
            //region 去掉this.priorityRoads中不在this.targetAreas中的road
            List<EntityID> removeList = new ArrayList<>(this.priorityRoads.size());
            for (EntityID id : this.priorityRoads)
            {
                if (!this.targetAreas.contains(id))
                {
                    removeList.add(id);
                }
            }
            this.priorityRoads.removeAll(removeList);
            //endregion
            
            //region 从this.priorityRoads中利用pathPlanning模块搜索目标
            if (this.priorityRoads.size() > 0)
            {
                this.pathPlanning.setFrom(positionID);
                this.pathPlanning.setDestination(this.targetAreas);
                List<EntityID> path = this.pathPlanning.calc().getResult();
                if (path != null && path.size() > 0)
                {
                    this.result = path.get(path.size() - 1);
                }
                return this;
            }
            //endregion
            
            //region 从this.targetAreas中利用pathPlanning模块搜索目标
            this.pathPlanning.setFrom(positionID);
            this.pathPlanning.setDestination(this.targetAreas);
            List<EntityID> path = this.pathPlanning.calc().getResult();
            if (path != null && path.size() > 0)
            {
                this.result = path.get(path.size() - 1);
            }
            //endregion
        }
        return this;
    }

    @Override
    public EntityID getTarget()
    {
        return this.result;
    }

    @Override
    public RoadDetector precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        return this;
    }

    /**
     * 重新计算this.targetAreas集合，this.priorityRoads集合
     */
    @Override
    public RoadDetector resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        
        //region 重新计算this.targetAreas集合，集合中的元素只要与任一Building、Refuge或GasStation邻接即可
        this.targetAreas = new HashSet<>();
        for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE, BUILDING, GAS_STATION))
        {
            for (EntityID id : ((Building) e).getNeighbours())
            {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if (neighbour instanceof Road)
                {
                    this.targetAreas.add(id);
                }
            }
        }
        //endregion
        
        //region 将与Refuge邻接的road设置为this.priorityRoads
        this.priorityRoads = new HashSet<>();
        for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE))
        {
            for (EntityID id : ((Building) e).getNeighbours())
            {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if (neighbour instanceof Road)
                {
                    this.priorityRoads.add(id);
                }
            }
        }
        //endregion
        return this;
    }

    /**
     * 计算this.targetAreas集合与this.priorityRoads集合
     */
    @Override
    public RoadDetector preparate()
    {
        super.preparate();
        if (this.getCountPreparate() >= 2)
        {
            return this;
        }
        //region 计算this.targetAreas集合，集合中的元素只要与任一Building、Refuge或GasStation邻接即可
        this.targetAreas = new HashSet<>();
        for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE, BUILDING, GAS_STATION))
        {
            for (EntityID id : ((Building) e).getNeighbours())
            {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if (neighbour instanceof Road)
                {
                    this.targetAreas.add(id);
                }
            }
        }
        //endregion
        
        //region 计算this.priorityRoads集合，集合中的元素为与Refuge邻接的road
        this.priorityRoads = new HashSet<>();
        for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE))
        {
            for (EntityID id : ((Building) e).getNeighbours())
            {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if (neighbour instanceof Road)
                {
                    this.priorityRoads.add(id);
                }
            }
        }
        return this;
    }

    /**
     * 根据world发生的变化（包括已经执行的动作的结果和传播的消息/命令），修改this.targetAreas和this.priorityRoads
     */
    @Override
    public RoadDetector updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2)
        {
            return this;
        }
        //region 更新this.result
        if (this.result != null)
        {
        	//如果Agent在this.result所指的Entity中
            if (this.agentInfo.getPosition().equals(this.result))
            {
                StandardEntity entity = this.worldInfo.getEntity(this.result);
                //如果this.result所指的Entity是building，则将this.result置为空
                if (entity instanceof Building)
                {
                    this.result = null;
                }
                //
                /* 如果this.result所指的Entity是Road，并且这条道路没有堵塞，则表示已到达目的地
                那么从this.targetAreas中移除this.result，并将this.result置为空 */
                else if (entity instanceof Road)
                {
                    Road road = (Road) entity;
                    if (!road.isBlockadesDefined() || road.getBlockades().isEmpty())
                    {
                        this.targetAreas.remove(this.result);
                        this.result = null;
                    }
                }
            }
        }
        //endregion
        
        //region 根据收到的消息，修改this.targetAreas和this.priorityRoads
        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        for (CommunicationMessage message : messageManager.getReceivedMessageList())
        {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if (messageClass == MessageAmbulanceTeam.class)
            {
                this.reflectMessage((MessageAmbulanceTeam) message);
            }
            else if (messageClass == MessageFireBrigade.class)
            {
                this.reflectMessage((MessageFireBrigade) message);
            }
            else if (messageClass == MessageRoad.class)
            {
                this.reflectMessage((MessageRoad) message, changedEntities);
            }
            else if (messageClass == MessagePoliceForce.class)
            {
                this.reflectMessage((MessagePoliceForce) message);
            }
            else if (messageClass == CommandPolice.class)
            {
                this.reflectMessage((CommandPolice) message);
            }
        }
        //endregion
        
        //region 根据地图的变化信息，修改this.targetAreas（从其中去掉通畅的道路）
        for (EntityID id : this.worldInfo.getChanged().getChangedEntities())
        {
            StandardEntity entity = this.worldInfo.getEntity(id);
            if (entity instanceof Road)
            {
                Road road = (Road) entity;
                //如果道路没有路障或者有路障但是被清理了，
                //那么这条道路是通畅的，把通畅的道路当做target没有意义，所以从this.targetAreas中去除
                if (!road.isBlockadesDefined() || road.getBlockades().isEmpty())
                {
                    this.targetAreas.remove(id);
                }
            }
        }
        //endregion
        return this;
    }

    /**
     * 根据Road类消息，修改“目标”
     * “目标”指this.targetAreas和this.priorityRoads
     * @param messageRoad
     * @param changedEntities
     */
    private void reflectMessage(MessageRoad messageRoad, Collection<EntityID> changedEntities)
    {
        if (messageRoad.isBlockadeDefined() && !changedEntities.contains(messageRoad.getBlockadeID()))
        {
            MessageUtil.reflectMessage(this.worldInfo, messageRoad);
        }
        //如果道路是通畅的，则在this.targetAreas中移除这个道路
        //因为道路如果通常，以道路为目标没有实际意义
        if (messageRoad.isPassable())
        {
            this.targetAreas.remove(messageRoad.getRoadID());
        }
    }

    /**
     * 根据AmbulanceTeam类消息，修改“目标”
     * “目标”指this.targetAreas和this.priorityRoads
     * @param messageAmbulanceTeam
     */
    private void reflectMessage(MessageAmbulanceTeam messageAmbulanceTeam)
    {
    	//如果救护车不在任何一个实体内，则根据此消息不能对“目标”做任何修改
        if (messageAmbulanceTeam.getPosition() == null)
        {
            return;
        }
        //如果救护车正在某个building内实施“救护”，那么从this.targetAreas内移除该building的所有邻居
        if (messageAmbulanceTeam.getAction() == MessageAmbulanceTeam.ACTION_RESCUE)
        {
            StandardEntity position = this.worldInfo.getEntity(messageAmbulanceTeam.getPosition());
            if (position != null && position instanceof Building)
            {
                this.targetAreas.removeAll(((Building) position).getNeighbours());
            }
        }
        //如果救护车正在某个building内实施“装载”，那么从this.targetAreas内移除该building的所有邻居
        else if (messageAmbulanceTeam.getAction() == MessageAmbulanceTeam.ACTION_LOAD)
        {
            StandardEntity position = this.worldInfo.getEntity(messageAmbulanceTeam.getPosition());
            if (position != null && position instanceof Building)
            {
                this.targetAreas.removeAll(((Building) position).getNeighbours());
            }
        }
        //如果救护车正在移动
        else if (messageAmbulanceTeam.getAction() == MessageAmbulanceTeam.ACTION_MOVE)
        {
        	//如果救护车正在无目标的移动，则不能对“目标”做修改
            if (messageAmbulanceTeam.getTargetID() == null)
            {
                return;
            }
            StandardEntity target = this.worldInfo.getEntity(messageAmbulanceTeam.getTargetID());
            //如果救护车移动的目标是某个building，则在this.priorityRoads中加入与这个building邻接的所有roads
            if (target instanceof Building)
            {
                for (EntityID id : ((Building) target).getNeighbours())
                {
                    StandardEntity neighbour = this.worldInfo.getEntity(id);
                    if (neighbour instanceof Road)
                    {
                        this.priorityRoads.add(id);
                    }
                }
            }
            //如果救护车移动的目标是某个Human
            else if (target instanceof Human)
            {
                Human human = (Human) target;
                //该human在某个Entity内
                if (human.isPositionDefined())
                {
                    StandardEntity position = this.worldInfo.getPosition(human);
                    //进一步判断: 如果该Human在某个building内，则在this.priorityRoads中加入与这个building邻接的所有roads
                    if (position instanceof Building)
                    {
                        for (EntityID id : ((Building) position).getNeighbours())
                        {
                            StandardEntity neighbour = this.worldInfo.getEntity(id);
                            if (neighbour instanceof Road)
                            {
                                this.priorityRoads.add(id);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 根据FireBrigade类消息，修改“目标”
     * “目标”指this.targetAreas和this.priorityRoads
     * @param messageFireBrigade
     */
    private void reflectMessage(MessageFireBrigade messageFireBrigade)
    {
    	//如果FireBrigade没有目标，则此消息没有任何意义
        if (messageFireBrigade.getTargetID() == null)
        {
            return;
        }
        //如果FireBrigade当前采取的行动为“加水”
        if (messageFireBrigade.getAction() == MessageFireBrigade.ACTION_REFILL)
        {
            StandardEntity target = this.worldInfo.getEntity(messageFireBrigade.getTargetID());
            //如果FireBrigade的目的地是某个building，则在this.priorityRoads中加入与这个building邻接的所有roads
            if (target instanceof Building)
            {
                for (EntityID id : ((Building) target).getNeighbours())
                {
                    StandardEntity neighbour = this.worldInfo.getEntity(id);
                    if (neighbour instanceof Road)
                    {
                        this.priorityRoads.add(id);
                    }
                }
            }
            //如果FireBrigade的目的地是某个消防栓，则在this.priorityRoads和this.targetAreas中加入这个target
            //消防栓是Road的子类
            else if (target.getStandardURN() == HYDRANT)
            {
                this.priorityRoads.add(target.getID());
                this.targetAreas.add(target.getID());
            }
        }
    }

    /**
     * 根据PoliceForce类消息，修改“目标”
     * “目标”指this.targetAreas和this.priorityRoads
     * @param messagePoliceForce
     */
    private void reflectMessage(MessagePoliceForce messagePoliceForce)
    {
    	//如果policeForce采取的行动是“清除路障”
        if (messagePoliceForce.getAction() == MessagePoliceForce.ACTION_CLEAR)
        {
        	//如果该消息不是当前agent发出的
        	//如果消息是当前agent发出的，则此消息对当前agent没有价值
            if (messagePoliceForce.getAgentID().getValue() != this.agentInfo.getID().getValue())
            {
            	//消息内包含“清除目标”的信息
                if (messagePoliceForce.isTargetDefined())
                {
                	//如果找不到target所指的Entity，直接退出
                    EntityID targetID = messagePoliceForce.getTargetID();
                    if (targetID == null)
                    {
                        return;
                    }
                    StandardEntity entity = this.worldInfo.getEntity(targetID);
                    if (entity == null)
                    {
                        return;
                    }
                    //如果目标是Area，从this.targetAreas中移除这个target(因为有policeForce已经移动向这个Area了)
                    //并根据规则this.result
                    if (entity instanceof Area)
                    {
                        this.targetAreas.remove(targetID);
                        //如果目标和清除的目标相同，并且当前Agent的ID小于消息中Agent的ID（为什么根据ID判断任务是否重复？？？），
                        //则清空当前目标：this.result
                        if (this.result != null && this.result.getValue() == targetID.getValue())
                        {
                            if (this.agentInfo.getID().getValue() < messagePoliceForce.getAgentID().getValue())
                            {
                                this.result = null;
                            }
                        }
                    }
                    //如果目标是路障的话，则从this.targetAreas中移除路障所在的Area
                    //并根据规则this.result
                    else if (entity.getStandardURN() == BLOCKADE)
                    {
                        EntityID position = ((Blockade) entity).getPosition();
                        this.targetAreas.remove(position);
                        if (this.result != null && this.result.getValue() == position.getValue())
                        {
                            if (this.agentInfo.getID().getValue() < messagePoliceForce.getAgentID().getValue())
                            {
                                this.result = null;
                            }
                        }
                    }

                }
            }
        }
    }

    /**
     * 根据commandPolice类消息，修改“目标”
     * “目标”指this.targetAreas和this.priorityRoads
     * @param commandPolice
     */
    private void reflectMessage(CommandPolice commandPolice)
    {
    	//flag表示这个Command是否为当前agent需要执行的命令
        boolean flag = false;
        //当前agent的id与command中的toID相等
        if (commandPolice.isToIDDefined() && this.agentInfo.getID().getValue() == commandPolice.getToID().getValue())
        {
            flag = true;
        }
        //command是广播
        else if (commandPolice.isBroadcast())
        {
            flag = true;
        }
        //是自己要执行的命令并且命令是clear
        if (flag && commandPolice.getAction() == CommandPolice.ACTION_CLEAR)
        {
            if (commandPolice.getTargetID() == null)
            {
                return;
            }
            StandardEntity target = this.worldInfo.getEntity(commandPolice.getTargetID());
            //如果target是Area，则将其加入this.priorityRoads和this.targetAreas
            if (target instanceof Area)
            {
                this.priorityRoads.add(target.getID());
                this.targetAreas.add(target.getID());
            }
            //如果target是路障，并且路障在某个Area，则将该Area加入this.priorityRoads和this.targetAreas
            else if (target.getStandardURN() == BLOCKADE)
            {
                Blockade blockade = (Blockade) target;
                if (blockade.isPositionDefined())
                {
                    this.priorityRoads.add(blockade.getPosition());
                    this.targetAreas.add(blockade.getPosition());
                }
            }
        }
    }
}
