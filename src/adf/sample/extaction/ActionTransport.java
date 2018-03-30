package adf.sample.extaction;

import adf.agent.action.Action;
import adf.agent.action.ambulance.ActionLoad;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.ambulance.ActionUnload;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import com.google.common.collect.Lists;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.BLOCKADE;
import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

/**
 * 计算要完成AmbulanceTeam运输的任务所要采取的行动，将该行动存储在this.result中
 * @author gxd
 */
public class ActionTransport extends ExtAction
{
    private PathPlanning pathPlanning;

    /**
     * agent收到的damage大于该值，表示需要休息
     */
    private int thresholdRest;
    
    private int kernelTime;

    private EntityID target;

    /**
     * 根据配置文件初始化pathPlanning模块
     * 注：父类不是继承自AbstractModule，所以没有维护的子模块列表
     * @param agentInfo
     * @param worldInfo
     * @param scenarioInfo
     * @param moduleManager
     * @param developData
     */
    public ActionTransport(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData)
    {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.target = null;
        this.thresholdRest = developData.getInteger("ActionTransport.rest", 100);

        switch (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("ActionTransport.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("ActionTransport.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("ActionTransport.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
        }
    }

    public ExtAction precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        try
        {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        }
        catch (NoSuchConfigOptionException e)
        {
            this.kernelTime = -1;
        }
        return this;
    }

    public ExtAction resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        try
        {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        }
        catch (NoSuchConfigOptionException e)
        {
            this.kernelTime = -1;
        }
        return this;
    }

    public ExtAction preparate()
    {
        super.preparate();
        if (this.getCountPreparate() >= 2)
        {
            return this;
        }
        this.pathPlanning.preparate();
        try
        {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        }
        catch (NoSuchConfigOptionException e)
        {
            this.kernelTime = -1;
        }
        return this;
    }

    public ExtAction updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2)
        {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        return this;
    }

    @Override
    public ExtAction setTarget(EntityID target)
    {
        this.target = null;
        if (target != null)
        {
            StandardEntity entity = this.worldInfo.getEntity(target);
            if (entity instanceof Human || entity instanceof Area)
            {
                this.target = target;
                return this;
            }
        }
        return this;
    }

    
    @Override
    public ExtAction calc()
    {
        this.result = null;
        AmbulanceTeam agent = (AmbulanceTeam) this.agentInfo.me();
        /**
         * AmbulanceTeam正在运载的Human
         */
        Human transportHuman = this.agentInfo.someoneOnBoard();
        
        //下面3个if的顺序很重要，可能是需要考虑优化的地方！！！！！！！！！！！！！！！！！！！！
        
        if (transportHuman != null)
        {
        	//返回要成功将此human卸载所要采取的行动
            this.result = this.calcUnload(agent, this.pathPlanning, transportHuman, this.target);
            if (this.result != null)
            {
                return this;
            }
        }
        //Agent需要先休息，返回相应的行动
        //目的：找到一个Refuge，到这个Refuge休息，并且这个Refuge能够到达target所在的Area
        if (this.needRest(agent))
        {
            EntityID areaID = this.convertArea(this.target);
            ArrayList<EntityID> targets = new ArrayList<>();
            if (areaID != null)
            {
                targets.add(areaID);
            }
            this.result = this.calcRefugeAction(agent, this.pathPlanning, targets, false);
            if (this.result != null)
            {
                return this;
            }
        }
        //如果有设置target，则返回营救该target所应采取的行动
        if (this.target != null)
        {
            this.result = this.calcRescue(agent, this.pathPlanning, this.target);
        }
        return this;
    }

    /**
     * 分析目标性质，返回需要采取的行动
     * 例如：如果目标是活着的、被掩埋的Human并且和自己在一个Area，则返回“ActionRescue”
     *      如果目标是路障的话，则需要到达这个路障所在的Area，返回“ActionMove”
     * @param agent AmbulanceTeam实例
     * @param pathPlanning 路径规划模块
     * @param targetID 目标的entityID，目标可以是Human也可以是Area
     * @return
     */
    private Action calcRescue(AmbulanceTeam agent, PathPlanning pathPlanning, EntityID targetID)
    {
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        if (targetEntity == null)
        {
            return null;
        }
        EntityID agentPosition = agent.getPosition();
        if (targetEntity instanceof Human)
        {
            Human human = (Human) targetEntity;
            if (!human.isPositionDefined())
            {
                return null;
            }
            if (human.isHPDefined() && human.getHP() == 0)
            {
                return null;
            }
            EntityID targetPosition = human.getPosition();
            //agent与human在一个Area上
            if (agentPosition.getValue() == targetPosition.getValue())
            {
            	//如果被掩埋，返回“营救行动”
                if (human.isBuriednessDefined() && human.getBuriedness() > 0)
                {
                    return new ActionRescue(human);
                }
                //如果没被掩埋，返回“装载行动”
                else if (human.getStandardURN() == CIVILIAN)
                {
                    return new ActionLoad(human.getID());
                }
            }
            //如果不在一个Area上，首先通过pathPlanning模块搜索一条能到达目标的可行路径，返回“移动行动”
            else
            {
                List<EntityID> path = pathPlanning.getResult(agentPosition, targetPosition);
                if (path != null && path.size() > 0)
                {
                    return new ActionMove(path);
                }
            }
            return null;
        }
        //如果目标是路障的话，将targetEntity设置为路障所在的Area，这样修改的目的是为了到达这个路障所在的Area
        if (targetEntity.getStandardURN() == BLOCKADE)
        {
            Blockade blockade = (Blockade) targetEntity;
            if (blockade.isPositionDefined())
            {
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        //如果目标是Area，首先通过pathPlanning模块搜索一条能到达目标的可行路径，返回“移动行动”
        if (targetEntity instanceof Area)
        {
            List<EntityID> path = pathPlanning.getResult(agentPosition, targetEntity.getID());
            if (path != null && path.size() > 0)
            {
                this.result = new ActionMove(path);
            }
        }
        return null;
    }

    /**
     * 已经装载了transportHuman前提下，要把该Human送到Refuge，计算现在应采取的行动
     * @param agent AmbulanceTeam实例
     * @param pathPlanning 路径规划模块
     * @param transportHuman
     * @param targetID 目标的entityID，目标可以是Human也可以是Area
     * @return
     */
    private Action calcUnload(AmbulanceTeam agent, PathPlanning pathPlanning, Human transportHuman, EntityID targetID)
    {
        if (transportHuman == null)
        {
            return null;
        }
        //如果transportHuman的HP已经为0，则返回“卸载行动”
        if (transportHuman.isHPDefined() && transportHuman.getHP() == 0)
        {
            return new ActionUnload();
        }
        EntityID agentPosition = agent.getPosition();
        //如果targetID不存在或者transportHuman和targetID实际是一个“东西”
        //此时要尝试找一个Refuge，把transportHuman送到该Refuge
        if (targetID == null || transportHuman.getID().getValue() == targetID.getValue())
        {
            StandardEntity position = this.worldInfo.getEntity(agentPosition);
            //如果当前Agent在Refuge，则返回“卸载行动”
            if (position != null && position.getStandardURN() == REFUGE)
            {
                return new ActionUnload();
            }
            //否则，利用pathPlanning搜索一条到Refuge的路径，返回“移动行动”
            else
            {
                pathPlanning.setFrom(agentPosition);
                pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
                List<EntityID> path = pathPlanning.calc().getResult();
                if (path != null && path.size() > 0)
                {
                    return new ActionMove(path);
                }
            }
        }
        if (targetID == null)
        {
            return null;
        }
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        //如果targetID实际代表的是路障的话，则将target调整为该路障所在的Area
        if (targetEntity != null && targetEntity.getStandardURN() == BLOCKADE)
        {
            Blockade blockade = (Blockade) targetEntity;
            if (blockade.isPositionDefined())
            {
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        //target如果是Area
        if (targetEntity instanceof Area)
        {
        	//如果Agent就在这个Area，返回“卸载行动”
            if (agentPosition.getValue() == targetID.getValue())
            {
                return new ActionUnload();
            }
            //否则，利用pathPlanning搜索一条到targetID的路径，返回“移动行动”
            else
            {
                pathPlanning.setFrom(agentPosition);
                pathPlanning.setDestination(targetID);
                List<EntityID> path = pathPlanning.calc().getResult();
                if (path != null && path.size() > 0)
                {
                    return new ActionMove(path);
                }
            }
        }
        //如果target是Human，则要先吧transportHuman送到某个Refuge，再从该Refuge到Human
        else if (targetEntity instanceof Human)
        {
            Human human = (Human) targetEntity;
            if (human.isPositionDefined())
            {
                return calcRefugeAction(agent, pathPlanning, Lists.newArrayList(human.getPosition()), true);
            }
            //最差情况，不考虑再到target的情况，只返回能从当前到某个Refuge的”移动行动“
            pathPlanning.setFrom(agentPosition);
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0)
            {
                return new ActionMove(path);
            }
        }
        return null;
    }

    /**
     * 判断Agent是否需要休息
     * @param agent
     * @return
     */
    private boolean needRest(Human agent)
    {
        int hp = agent.getHP();
        int damage = agent.getDamage();
        if (hp == 0 || damage == 0)
        {
            return false;
        }
        //hp/damage向上取整
        int activeTime = (hp / damage) + ((hp % damage) != 0 ? 1 : 0);
        if (this.kernelTime == -1)
        {
            try
            {
                this.kernelTime = this.scenarioInfo.getKernelTimesteps();
            }
            catch (NoSuchConfigOptionException e)
            {
                this.kernelTime = -1;
            }
        }
        return damage >= this.thresholdRest || (activeTime + this.agentInfo.getTime()) < this.kernelTime;
    }

    /**
     * 返回targetID所在的Area
     * @param targetID 可以是Human或者Area或者路障
     * @return
     */
    private EntityID convertArea(EntityID targetID)
    {
        StandardEntity entity = this.worldInfo.getEntity(targetID);
        if (entity == null)
        {
            return null;
        }
        if (entity instanceof Human)
        {
            Human human = (Human) entity;
            if (human.isPositionDefined())
            {
                EntityID position = human.getPosition();
                if (this.worldInfo.getEntity(position) instanceof Area)
                {
                    return position;
                }
            }
        }
        else if (entity instanceof Area)
        {
            return targetID;
        }
        else if (entity.getStandardURN() == BLOCKADE)
        {
            Blockade blockade = (Blockade) entity;
            if (blockade.isPositionDefined())
            {
                return blockade.getPosition();
            }
        }
        return null;
    }

    /**
     * 尝试找一条从human到某个Refuge的”较优“路径
     * 注：
     *   1.如果human就在某个Refuge的话，返回“卸载行动”/“休息行动“
     *   2.尝试找到一个Refuge，使得human到Refuge存在路径，并且该Refuge能到达targets集合中的某个
     * @param human 已经装载的某个目标
     * @param pathPlanning
     * @param targets 后续目标集合
     * @param isUnload true表示到Refuge的目的是卸载，false表示到Refuge的目的是休息
     * @return
     */
    private Action calcRefugeAction(Human human, PathPlanning pathPlanning, Collection<EntityID> targets, boolean isUnload)
    {
        EntityID position = human.getPosition();
        Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE);
        int size = refuges.size();
        //如果Human就在某个Refuge的话，根据isUnload标志返回”卸载行动“或”休息行动“
        if (refuges.contains(position))
        {
            return isUnload ? new ActionUnload() : new ActionRest();
        }
        List<EntityID> firstResult = null;
        /**从pathPlanning以此寻找满足下列条件的Refuge，根据找到的第一个返回”移动行动“
        * 1.能到达该Refuge
        * 2.该Refuge能到达targets集合中的某个
        * 如果没有满足上述性质的Refuge，则仅按性质1寻找
        */
        while (refuges.size() > 0)
        {
            pathPlanning.setFrom(position);
            pathPlanning.setDestination(refuges);
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0)
            {
                if (firstResult == null)
                {
                    firstResult = new ArrayList<>(path);
                    if (targets == null || targets.isEmpty())
                    {
                        break;
                    }
                }
                EntityID refugeID = path.get(path.size() - 1);
                pathPlanning.setFrom(refugeID);
                pathPlanning.setDestination(targets);
                List<EntityID> fromRefugeToTarget = pathPlanning.calc().getResult();
                if (fromRefugeToTarget != null && fromRefugeToTarget.size() > 0)
                {
                    return new ActionMove(path);
                }
                refuges.remove(refugeID);
                //remove failed
                if (size == refuges.size())
                {
                    break;
                }
                size = refuges.size();
            }
            else
            {
                break;
            }
        }
        return firstResult != null ? new ActionMove(firstResult) : null;
    }
}
