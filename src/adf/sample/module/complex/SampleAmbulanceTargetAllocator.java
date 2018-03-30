package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandAmbulance;
import adf.agent.communication.standard.bundle.centralized.MessageReport;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageCivilian;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.AmbulanceTargetAllocator;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 
 * @author gxd
 */
public class SampleAmbulanceTargetAllocator extends AmbulanceTargetAllocator
{
    private Collection<EntityID> priorityHumans;
    private Collection<EntityID> targetHumans;

    /**
     * ambulanceTeam->任务
     */
    private Map<EntityID, AmbulanceTeamInfo> ambulanceTeamInfoMap;

    
    public SampleAmbulanceTargetAllocator(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);
        this.priorityHumans = new HashSet<>();
        this.targetHumans = new HashSet<>();
        this.ambulanceTeamInfoMap = new HashMap<>();
    }

    /**
     * 重置this.ambulanceTeamInfoMap
     */
    @Override
    public AmbulanceTargetAllocator resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        for (EntityID id : this.worldInfo.getEntityIDsOfType(StandardEntityURN.AMBULANCE_TEAM))
        {
            this.ambulanceTeamInfoMap.put(id, new AmbulanceTeamInfo(id));
        }
        return this;
    }
    
    /**
     * 重置this.ambulanceTeamInfoMap
     */
    @Override
    public AmbulanceTargetAllocator preparate()
    {
        super.preparate();
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        for (EntityID id : this.worldInfo.getEntityIDsOfType(StandardEntityURN.AMBULANCE_TEAM))
        {
            this.ambulanceTeamInfoMap.put(id, new AmbulanceTeamInfo(id));
        }
        return this;
    }

    /**
     * 返回一个map，每个ambulanceTeam对应的Target
     */
    @Override
    public Map<EntityID, EntityID> getResult()
    {
        return this.convert(this.ambulanceTeamInfoMap);
    }
    
    /**
     * 尝试为能分配任务的AmbulanceTeam分配一个需要营救的Human
     */
    @Override
    public AmbulanceTargetAllocator calc()
    {
    	//获取能分配action的AmbulanceTeam
        List<StandardEntity> agents = this.getActionAgents(this.ambulanceTeamInfoMap);
        Collection<EntityID> removes = new ArrayList<>();
        int currentTime = this.agentInfo.getTime();
        //region 尝试为this.priorityHumans中的human分配AmbulanceTeam
        for (EntityID target : this.priorityHumans)
        {
        	//还有能分配任务的AmbulanceTeam
            if (agents.size() > 0)
            {
                StandardEntity targetEntity = this.worldInfo.getEntity(target);
                //该Human要在某个Area上
                if (targetEntity != null && targetEntity instanceof Human && ((Human) targetEntity).isPositionDefined())
                {
                	//调选距离该Human最近的AmbulanceTeam
                    agents.sort(new DistanceSorter(this.worldInfo, targetEntity));
                    StandardEntity result = agents.get(0);
                    agents.remove(0);
                    //构造ambulanceTeamInfo
                    AmbulanceTeamInfo info = this.ambulanceTeamInfoMap.get(result.getID());
                    if (info != null)
                    {
                        info.canNewAction = false;
                        info.target = target;
                        info.commandTime = currentTime;
                        this.ambulanceTeamInfoMap.put(result.getID(), info);
                        removes.add(target);
                    }
                }
            }
        }
        //从this.priorityHumans中移除上面计算出的可以营救的human
        this.priorityHumans.removeAll(removes);
        removes.clear();
        //endregion
        //region 尝试为this.targetHumans中的human分配AmbulanceTeam
        for (EntityID target : this.targetHumans)
        {
            if (agents.size() > 0)
            {
                StandardEntity targetEntity = this.worldInfo.getEntity(target);
                if (targetEntity != null && targetEntity instanceof Human && ((Human) targetEntity).isPositionDefined())
                {
                    agents.sort(new DistanceSorter(this.worldInfo, targetEntity));
                    StandardEntity result = agents.get(0);
                    agents.remove(0);
                    AmbulanceTeamInfo info = this.ambulanceTeamInfoMap.get(result.getID());
                    if (info != null)
                    {
                        info.canNewAction = false;
                        info.target = target;
                        info.commandTime = currentTime;
                        this.ambulanceTeamInfoMap.put(result.getID(), info);
                        removes.add(target);
                    }
                }
            }
        }
        this.targetHumans.removeAll(removes);
        //endregion
        return this;
    }

    /**
     * 根据接受到的消息、命令等，更新维护的“AmbulanceTeam->任务”信息
     */
    @Override
    public AmbulanceTargetAllocator updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2)
        {
            return this;
        }
        int currentTime = this.agentInfo.getTime();
        //遍历每一个接收到的消息，更新this.targetHumans和this.priorityHumans
        for (CommunicationMessage message : messageManager.getReceivedMessageList())
        {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            //如果消息是关于Human的，
            //如果消息传递的是civilian被掩埋的信息，则将此Human加入this.targetHumans、
            //如果消息中的人没有被掩埋，则从this.targetHumans和this.priorityHumans中移除消息中的human
            if (messageClass == MessageCivilian.class)
            {
                MessageCivilian mc = (MessageCivilian) message;
                MessageUtil.reflectMessage(this.worldInfo, mc);
                if (mc.isBuriednessDefined() && mc.getBuriedness() > 0)
                {
                    this.targetHumans.add(mc.getAgentID());
                }
                else
                {
                    this.priorityHumans.remove(mc.getAgentID());
                    this.targetHumans.remove(mc.getAgentID());
                }
            }
            //如果消息是关于消防员的，
            //如果消息传递的是消防员被掩埋的信息，则将此消防员加入this.priorityHumans、
            //如果消息中的消防员没有被掩埋，则从this.targetHumans和this.priorityHumans中移除消息中的消防员
            else if (messageClass == MessageFireBrigade.class)
            {
                MessageFireBrigade mfb = (MessageFireBrigade) message;
                MessageUtil.reflectMessage(this.worldInfo, mfb);
                if (mfb.isBuriednessDefined() && mfb.getBuriedness() > 0)
                {
                    this.priorityHumans.add(mfb.getAgentID());
                }
                else
                {
                    this.priorityHumans.remove(mfb.getAgentID());
                    this.targetHumans.remove(mfb.getAgentID());
                }
            }
            //如果消息是关于警察的，
            //如果消息传递的是警察被掩埋的信息，则将此警察加入this.priorityHumans、
            //如果消息中的警察没有被掩埋，则从this.targetHumans和this.priorityHumans中移除消息中的警察
            else if (messageClass == MessagePoliceForce.class)
            {
                MessagePoliceForce mpf = (MessagePoliceForce) message;
                MessageUtil.reflectMessage(this.worldInfo, mpf);
                if (mpf.isBuriednessDefined() && mpf.getBuriedness() > 0)
                {
                    this.priorityHumans.add(mpf.getAgentID());
                }
                else
                {
                    this.priorityHumans.remove(mpf.getAgentID());
                    this.targetHumans.remove(mpf.getAgentID());
                }
            }
        }
        //???????????????????????????????????????????????????????????????
        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageAmbulanceTeam.class))
        {
            MessageAmbulanceTeam mat = (MessageAmbulanceTeam) message;
            MessageUtil.reflectMessage(this.worldInfo, mat);
            if (mat.isBuriednessDefined() && mat.getBuriedness() > 0)
            {
                this.priorityHumans.add(mat.getAgentID());
            }
            else
            {
                this.priorityHumans.remove(mat.getAgentID());
                this.targetHumans.remove(mat.getAgentID());
            }
            AmbulanceTeamInfo info = this.ambulanceTeamInfoMap.get(mat.getAgentID());
            if (info == null)
            {
                info = new AmbulanceTeamInfo(mat.getAgentID());
            }
            if (currentTime >= info.commandTime + 2)
            {
                this.ambulanceTeamInfoMap.put(mat.getAgentID(), this.update(info, mat));
            }
        }
        for (CommunicationMessage message : messageManager.getReceivedMessageList(CommandAmbulance.class))
        {
            CommandAmbulance command = (CommandAmbulance) message;
            if (command.getAction() == CommandAmbulance.ACTION_RESCUE && command.isBroadcast())
            {
                this.priorityHumans.add(command.getTargetID());
                this.targetHumans.add(command.getTargetID());
            }
            else if (command.getAction() == CommandAmbulance.ACTION_LOAD && command.isBroadcast())
            {
                this.priorityHumans.add(command.getTargetID());
                this.targetHumans.add(command.getTargetID());
            }
        }
        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageReport.class))
        {
            MessageReport report = (MessageReport) message;
            AmbulanceTeamInfo info = this.ambulanceTeamInfoMap.get(report.getSenderID());
            if (info != null && report.isDone())
            {
                info.canNewAction = true;
                this.priorityHumans.remove(info.target);
                this.targetHumans.remove(info.target);
                info.target = null;
                this.ambulanceTeamInfoMap.put(info.agentID, info);
            }
        }
        return this;
    }
    
    /**
     * 将map<ambulanceTeamID, ambulanceTeamInfo>转换成map<ambulanceTeamID, targetID>
     * @param map
     * @return
     */
    private Map<EntityID, EntityID> convert(Map<EntityID, AmbulanceTeamInfo> map)
    {
        Map<EntityID, EntityID> result = new HashMap<>();
        for (EntityID id : map.keySet())
        {
            AmbulanceTeamInfo info = map.get(id);
            if (info != null && info.target != null)
            {
                result.put(id, info.target);
            }
        }
        return result;
    }
    
    /**
     * 获取能分配action的AmbulanceTeam
     * @param map
     * @return
     */
    private List<StandardEntity> getActionAgents(Map<EntityID, AmbulanceTeamInfo> map)
    {
        List<StandardEntity> result = new ArrayList<>();
        //应该为StandardEntityURN.AMBULANCE_TEAM！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
        //遍历所有AmbulanceTeam，如果这个AmbulanceTeam能被分配新action并且在某个Area上，则可为其分配新任务
        for (StandardEntity entity : this.worldInfo.getEntitiesOfType(StandardEntityURN.POLICE_FORCE))
        {
            AmbulanceTeamInfo info = map.get(entity.getID());
            if (info != null && info.canNewAction && ((AmbulanceTeam) entity).isPositionDefined())
            {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * 根据AmbulanceTeam之前发布的某个message或者接收到的命令，
     * 相应修改自己的AmbulanceTeamInfo（任务信息）,this.targetHumans,this.priorityHumans
     * @param info
     * @param message
     * @return
     */
    private AmbulanceTeamInfo update(AmbulanceTeamInfo info, MessageAmbulanceTeam message)
    {
    	//如果当前AmbulanceTeam发布了自己被掩埋的消息，
    	//则这个AmbulanceTeam不能接受新的任务并且要将他的任务放到this.targetHumans(???这个任务的来源可能是this.priorityHumans)
        if (message.isBuriednessDefined() && message.getBuriedness() > 0)
        {
            info.canNewAction = false;
            if (info.target != null)
            {
                this.targetHumans.add(info.target);
                info.target = null;
            }
            return info;
        }
        //如果当前AmbulanceTeam发布自己需要休息的消息
        //则要将他的目标放回到this.priorityHumans中
        if (message.getAction() == MessageAmbulanceTeam.ACTION_REST)
        {
            info.canNewAction = true;
            if (info.target != null)
            {
                this.targetHumans.add(info.target);
                info.target = null;
            }
        }
        //如果当前AmbulanceTeam发布自己正在移动的消息
        //
        else if (message.getAction() == MessageAmbulanceTeam.ACTION_MOVE)
        {
            if (message.getTargetID() != null)
            {
                StandardEntity entity = this.worldInfo.getEntity(message.getTargetID());
                if (entity != null)
                {
                    if (entity instanceof Area)
                    {
                    	//如果当前AmbulanceTeam正在向Refuge移动，则不能为他分配任务
                        if (entity.getStandardURN() == REFUGE)
                        {
                            info.canNewAction = false;
                            return info;
                        }
                        StandardEntity targetEntity = this.worldInfo.getEntity(info.target);
                        
                        if (targetEntity != null)
                        {
                        	//如果当前AmbulanceTeam正在向某个Area移动，
                            //并且info中记录的是要救某个Human
                            //但是Human所在的Area已经没了（可能是楼塌了等原因）
                            //此时可以重新为该AmbulanceTeam分配任务
                            if (targetEntity instanceof Human)
                            {
                                targetEntity = this.worldInfo.getPosition((Human) targetEntity);
                                if (targetEntity == null)
                                {
                                    this.priorityHumans.remove(info.target);
                                    this.targetHumans.remove(info.target);
                                    info.canNewAction = true;
                                    info.target = null;
                                    return info;
                                }
                            }
                            //如果info中记录的target与message中记录的target相同的话，则不能为该AmbulanceTeam分配新任务
                            if (targetEntity.getID().getValue() == entity.getID().getValue())
                            {
                                info.canNewAction = false;
                            }
                            //否则，重置该AmbulanceTeam的任务
                            else
                            {
                                info.canNewAction = true;
                                if (info.target != null)
                                {
                                    this.targetHumans.add(info.target);
                                    info.target = null;
                                }
                            }
                        }
                        //info中记录的target实际不存在时，可以为该AmbulanceTeam分配新任务
                        else
                        {
                            info.canNewAction = true;
                            info.target = null;
                        }
                        return info;
                    }
                    //message记录的目标如果是Human的话
                    else if (entity instanceof Human)
                    {
                    	//如果message记录的目标与Info记录的目标相同的话，不能为该AmbulanceTeam指派新任务
                        if (entity.getID().getValue() == info.target.getValue())
                        {
                            info.canNewAction = false;
                        }
                        //否则，重置该AmbulanceTeam的任务
                        else
                        {
                            info.canNewAction = true;
                            this.targetHumans.add(info.target);
                            this.targetHumans.add(entity.getID());
                            info.target = null;
                        }
                        return info;
                    }
                }
            }
            info.canNewAction = true;
            //如果message中记录的目标实际不存在，info中却记录了目标
            //则重置该AmbulanceTeam的任务
            if (info.target != null)
            {
                this.targetHumans.add(info.target);
                info.target = null;
            }
        }
        else if (message.getAction() == MessageAmbulanceTeam.ACTION_RESCUE)
        {
            info.canNewAction = true;
            if (info.target != null)
            {
                this.targetHumans.add(info.target);
                info.target = null;
            }
        }
        //如果当前AmbulanceTeam发布自己正在装载的消息
        //则不能为其分配新任务
        else if (message.getAction() == MessageAmbulanceTeam.ACTION_LOAD)
        {
            info.canNewAction = false;
        }
        //如果当前AmbulanceTeam发布自己正在卸载的消息
        //则表示其已经完成任务，可以重新安排任务
        else if (message.getAction() == MessageAmbulanceTeam.ACTION_UNLOAD)
        {
            info.canNewAction = true;
            this.priorityHumans.remove(info.target);
            this.targetHumans.remove(info.target);
            info.target = null;
        }
        return info;
    }

    /**
     * 记录AmbulanceTeam任务信息
     * @author gxd
     */
    private class AmbulanceTeamInfo
    {
        EntityID agentID;
        EntityID target;
        boolean canNewAction;
        int commandTime;

        AmbulanceTeamInfo(EntityID id)
        {
            agentID = id;
            target = null;
            canNewAction = true;
            commandTime = -1;
        }
    }
    
    /**
     * 比较器：判断a和b到reference距离的大小
     * @author gxd
     */
    private class DistanceSorter implements Comparator<StandardEntity>
    {
        private StandardEntity reference;
        private WorldInfo worldInfo;

        DistanceSorter(WorldInfo wi, StandardEntity reference)
        {
            this.reference = reference;
            this.worldInfo = wi;
        }

        public int compare(StandardEntity a, StandardEntity b)
        {
            int d1 = this.worldInfo.getDistance(this.reference, a);
            int d2 = this.worldInfo.getDistance(this.reference, b);
            return d1 - d2;
        }
    }
}
