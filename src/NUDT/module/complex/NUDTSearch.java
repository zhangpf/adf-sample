package NUDT.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.Search;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 搜索未被探索过的Building
 * @author gxd
 */
public class NUDTSearch extends Search
{
    private PathPlanning pathPlanning;
    private Clustering clustering;

    private EntityID result;
    private Collection<EntityID> unsearchedBuildingIDs;

    /**
     * 根据配置文件初始化pathPlanning模块和clustering模块
     * @param ai
     * @param wi
     * @param si
     * @param moduleManager
     * @param developData
     */
    public NUDTSearch(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);

        this.unsearchedBuildingIDs = new HashSet<>();

        StandardEntityURN agentURN = ai.me().getStandardURN();
        switch (si.getMode())
        {
            case PRECOMPUTATION_PHASE:
                if (agentURN == AMBULANCE_TEAM)
                {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                }
                else if (agentURN == FIRE_BRIGADE)
                {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                }
                else if (agentURN == POLICE_FORCE)
                {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
            case PRECOMPUTED:
                if (agentURN == AMBULANCE_TEAM)
                {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                }
                else if (agentURN == FIRE_BRIGADE)
                {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                }
                else if (agentURN == POLICE_FORCE)
                {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
            case NON_PRECOMPUTE:
                if (agentURN == AMBULANCE_TEAM)
                {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                }
                else if (agentURN == FIRE_BRIGADE)
                {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                }
                else if (agentURN == POLICE_FORCE)
                {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
        }

        registerModule(this.pathPlanning);
        registerModule(this.clustering);
    }


    /**
     * 根据地图信息的变化更新unsearchedBuildingIDs
     */
    @Override
    public Search updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2)
        {
            return this;
        }

        this.unsearchedBuildingIDs.removeAll(this.worldInfo.getChanged().getChangedEntities());

        if (this.unsearchedBuildingIDs.isEmpty())
        {
            this.reset();
            this.unsearchedBuildingIDs.removeAll(this.worldInfo.getChanged().getChangedEntities());
        }
        return this;
    }

    /**
     * 根据pathPlanning模块选择出一条agent所在Area到this.unsearchedBuildingIDs集合中某一个Building的最短路径
     * 并将这个Building设置为this.result
     */
    @Override
    public Search calc()
    {
        this.result = null;
        this.pathPlanning.setFrom(this.agentInfo.getPosition());
        this.pathPlanning.setDestination(this.unsearchedBuildingIDs);
        List<EntityID> path = this.pathPlanning.calc().getResult();
        if (path != null && path.size() > 0)
        {
            this.result = path.get(path.size() - 1);
        }
        return this;
    }
    
    /**
     * 重置未搜索的建筑ID序列this.unsearchedBuildingIDs
     * 具体工作过程：
     * 如果当前agent在一个簇中，则将这个簇的所有非Refuge的buildings加入unseachedBuildingIDs
     * 否则，将地图上所有的Building加入unseachedBuildingIDs
     */
    private void reset()
    {
        this.unsearchedBuildingIDs.clear();
        
        //得到当前agent所在的簇
        Collection<StandardEntity> clusterEntities = null;
        if (this.clustering != null)
        {
            int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
            clusterEntities = this.clustering.getClusterEntities(clusterIndex);

        }
        //如果存在这样簇，将这个簇内的所有building加入this.unsearchedBuildingIDs
        if (clusterEntities != null && clusterEntities.size() > 0)
        {
            for (StandardEntity entity : clusterEntities)
            {
                if (entity instanceof Building && entity.getStandardURN() != REFUGE)
                {
                    this.unsearchedBuildingIDs.add(entity.getID());
                }
            }
        }
        //不存在这样的簇，将“下述”类型的Entity加入this.unsearchedBuildingIDs
        else
        {
            this.unsearchedBuildingIDs.addAll(this.worldInfo.getEntityIDsOfType(
                    BUILDING,
                    GAS_STATION,
                    AMBULANCE_CENTRE,
                    FIRE_STATION,
                    POLICE_OFFICE
            ));
        }
    }

    @Override
    public EntityID getTarget()
    {
        return this.result;
    }

    @Override
    public Search precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public Search resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        this.worldInfo.requestRollback();
        return this;
    }

    @Override
    public Search preparate()
    {
        super.preparate();
        if (this.getCountPreparate() >= 2)
        {
            return this;
        }
        this.worldInfo.requestRollback();
        return this;
    }
}