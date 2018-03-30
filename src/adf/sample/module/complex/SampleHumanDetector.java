package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.communication.standard.bundle.centralized.CommandFire;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.HumanDetector;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 
 * @author gxd
 */
public class SampleHumanDetector extends HumanDetector
{
	/**
	 * 聚类模块
	 */
    private Clustering clustering;

    /**
     * 下一个要寻找的Human
     */
    private EntityID result;

    /**
     * 根据配置信息初始化clustering模块
     * @param ai
     * @param wi
     * @param si
     * @param moduleManager
     * @param developData
     */
    public SampleHumanDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);

        this.result = null;

        switch (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
                this.clustering = moduleManager.getModule("SampleHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case PRECOMPUTED:
                this.clustering = moduleManager.getModule("SampleHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case NON_PRECOMPUTE:
                this.clustering = moduleManager.getModule("SampleHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
        }
        registerModule(this.clustering);
    }

    @Override
    public HumanDetector updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() > 1)
        {
            return this;
        }

        return this;
    }

    /**
     * 更新或者重置this.result（下一个要寻找的human）
     */
    @Override
    public HumanDetector calc()
    {
    	//获得当前Agent上的civilian
        Human transportHuman = this.agentInfo.someoneOnBoard();
        //如果当前Agent上有civilian
        if (transportHuman != null)
        {
            this.result = transportHuman.getID();
            return this;
        }
        //目标不为空时
        if (this.result != null)
        {
            Human target = (Human) this.worldInfo.getEntity(this.result);
            if (target != null)
            {
            	//如果当前目标的hp没有定义或者为0时，抛弃这个目标
                if (!target.isHPDefined() || target.getHP() == 0)
                {
                    this.result = null;
                }
                //如果目标不在任何Area或Entity上，清空this.result
                else if (!target.isPositionDefined())
                {
                    this.result = null;
                }
                else
                {
                    StandardEntity position = this.worldInfo.getPosition(target);
                    //如果目标已经在Refuge或者ambulanceTeam，则不需要再继续搜索这个目标，清空即可
                    if (position != null)
                    {
                        StandardEntityURN positionURN = position.getStandardURN();
                        if (positionURN == REFUGE || positionURN == AMBULANCE_TEAM)
                        {
                            this.result = null;
                        }
                    }
                }
            }
        }
        //下面这行不能是else，因为上面的代码块有可能清空this.result，对于this.result == null的情况，要重新为其分配值
        if (this.result == null)
        {
        	//没有clustering模块时，从整个world中寻找目标
            if (clustering == null)
            {
                this.result = this.calcTargetInWorld();
                return this;
            }
            //否则从当前Agent所在的clustering搜索目标
            this.result = this.calcTargetInCluster(clustering);
            //如果从Agent所在的clustering中搜索不到目标，则再在整个world中搜索目标
            if (this.result == null)
            {
                this.result = this.calcTargetInWorld();
            }
        }
        return this;
    }

    /**
     * 根据clustering模块，当前Agent在自己所处的簇中搜索目标
     * @param clustering
     * @return
     */
    private EntityID calcTargetInCluster(Clustering clustering)
    {
    	//获得自己所在的簇
        int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
        //当前簇包含的所有Entity
        Collection<StandardEntity> elements = clustering.getClusterEntities(clusterIndex);
        if (elements == null || elements.isEmpty())
        {
            return null;
        }

        List<Human> rescueTargets = new ArrayList<>();
        List<Human> loadTargets = new ArrayList<>();
        //region 将被掩埋并且还活着的营救员、消防员、警察加进rescueTargets
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE))
        {
            Human h = (Human) next;
            if (this.agentInfo.getID().getValue() == h.getID().getValue())
            {
                continue;
            }
            //h所在的Entity
            StandardEntity positionEntity = this.worldInfo.getPosition(h);
            //Agent所在的簇包含h或者h所在的Entity
            if (positionEntity != null && elements.contains(positionEntity) || elements.contains(h))
            {
            	//h被掩埋并且还活着
                if (h.isHPDefined() && h.isBuriednessDefined() && h.getHP() > 0 && h.getBuriedness() > 0)
                {
                    rescueTargets.add(h);
                }
            }
        }
        //endregion
        
        //region 如果civilian所在的area在簇中，
        //如果civilian被掩埋并且还活着，则将其加入rescueTargets，
        //如果civilian受伤了并且不在Refuge，则将其加入loadTargets
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(CIVILIAN))
        {
            Human h = (Human) next;
            StandardEntity positionEntity = this.worldInfo.getPosition(h);
            //h在某个Area上
            if (positionEntity != null && positionEntity instanceof Area)
            {
            	//该Area在簇中
                if (elements.contains(positionEntity))
                {
                	//h还活着
                    if (h.isHPDefined() && h.getHP() > 0)
                    {
                    	//h被掩埋，则将其加入rescueTargets
                        if (h.isBuriednessDefined() && h.getBuriedness() > 0)
                        {
                            rescueTargets.add(h);
                        }
                        //h受伤并且不在Refuge，则将其加入loadTargets
                        else
                        {
                            if (h.isDamageDefined() && h.getDamage() > 0 && positionEntity.getStandardURN() != REFUGE)
                            {
                                loadTargets.add(h);
                            }
                        }
                    }
                }
            }
        }
        //endregion
        
        //region 分别将rescueTargets与loadTargets中的目标，按到当前Agent距离由近到远排序，取距离最近的营救或者装载
        if (rescueTargets.size() > 0)
        {
            rescueTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return rescueTargets.get(0).getID();
        }
        if (loadTargets.size() > 0)
        {
            loadTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return loadTargets.get(0).getID();
        }
        //endregion
        return null;
    }
    
    /**
     * 从世界模型中寻找目标
     * @return
     */
    private EntityID calcTargetInWorld()
    {
        List<Human> rescueTargets = new ArrayList<>();
        List<Human> loadTargets = new ArrayList<>();
        //region 将被掩埋但还活着的营救员、消防员、警察放入rescueTargets
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE))
        {
            Human h = (Human) next;
            if (this.agentInfo.getID().getValue() != h.getID().getValue())
            {
                StandardEntity positionEntity = this.worldInfo.getPosition(h);
                if (positionEntity != null && h.isHPDefined() && h.isBuriednessDefined())
                {
                    if (h.getHP() > 0 && h.getBuriedness() > 0)
                    {
                        rescueTargets.add(h);
                    }
                }
            }
        }
        //endregion
        
        //region 将被掩埋并且还活着的平民加入rescueTargets，将没有被掩埋但受伤了且不在Refuge的平民加入loadTargets
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(CIVILIAN))
        {
            Human h = (Human) next;
            StandardEntity positionEntity = this.worldInfo.getPosition(h);
            //该平民在某个Area上
            if (positionEntity != null && positionEntity instanceof Area)
            {
            	//该平民还活着
                if (h.isHPDefined() && h.getHP() > 0)
                {
                	//如果是被掩埋了，则将其加入rescueTargets
                    if (h.isBuriednessDefined() && h.getBuriedness() > 0)
                    {
                        rescueTargets.add(h);
                    }
                    else
                    {
                    	//如果是受伤了，并且不在Refuge，则将其加入loadTargets
                        if (h.isDamageDefined() && h.getDamage() > 0 && positionEntity.getStandardURN() != REFUGE)
                        {
                            loadTargets.add(h);
                        }
                    }
                }
            }
        }
        //endregion
        
        //region 分别将rescueTargets与loadTargets中的目标，按到当前Agent距离由近到远排序，取距离最近的营救或者装载
        if (rescueTargets.size() > 0)
        {
            rescueTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return rescueTargets.get(0).getID();
        }
        if (loadTargets.size() > 0)
        {
            loadTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return loadTargets.get(0).getID();
        }
        //endregion
        return null;
    }

    @Override
    public EntityID getTarget()
    {
        return this.result;
    }

    @Override
    public HumanDetector precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public HumanDetector resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public HumanDetector preparate()
    {
        super.preparate();
        if (this.getCountPreparate() >= 2)
        {
            return this;
        }
        return this;
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

