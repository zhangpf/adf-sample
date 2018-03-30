package adf.sample.module.algorithm;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.DynamicClustering;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * 当world中火情变化时，调整“着火建筑”的聚类信息
 * @author gxd
 */
public class SampleFireClustering extends DynamicClustering
{
	/**
	 * 聚类的标准
	 */
    private int groupingDistance;
    
    /**
     * 簇的集合
     * ???未找到在哪里初始化
     */
    List<List<StandardEntity>> clusterList = new LinkedList<>();

    public SampleFireClustering(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);
        this.groupingDistance = developData.getInteger("adf.sample.module.algorithm.SampleFireClustering.groupingDistance", 30);
    }

    /**
     * calculation phase; update cluster
     * 
     * 世界地图变化时（当有建筑着火或者灭火时），更新着火建筑的聚类
     * 建筑a着火时：
     *     a不能分进任何一个簇，此时a自成一个新簇，加入到簇集合中
     *     a只能分进一个簇，直接将a加进这个簇
     *     a能分进k(k>1)簇，将这k个簇连同a合并成为1个新簇
     * 建筑a灭火时：
     *     a自己在一个簇，将a所在的簇删除
     *     a在的簇拥有n(n > 1)个元素，剩余的n-1个元素根据划分标准，重新分成若干个簇
     * 
     * @return own instance for method chaining
     */
    @Override
    public Clustering calc()
    {
        for (EntityID changed : worldInfo.getChanged().getChangedEntities())
        {
            StandardEntity changedEntity = worldInfo.getEntity(changed);
            if (changedEntity.getStandardURN().equals(StandardEntityURN.BUILDING))
            { // changedEntity(cE) is a building
                Building changedBuilding = (Building)changedEntity;
                if (this.getClusterIndex(changedEntity) < 0)
                { // cE is not contained in cluster
                    if (isBurning(changedBuilding))
                    { // cE is burning building
                        ArrayList<EntityID> hostClusterPropertyEntityIDs = new ArrayList<>();

                        // search host cluster
                        for (List<StandardEntity> cluster : this.clusterList)
                        {
                            for (StandardEntity entity : cluster)
                            {
                                if (worldInfo.getDistance(entity, changedBuilding) <= groupingDistance)
                                {
                                    hostClusterPropertyEntityIDs.add(entity.getID());
                                    break;
                                }
                            }
                        }

                        if (hostClusterPropertyEntityIDs.size() == 0)
                        { // there is not host cluster : form new cluster
                            List<StandardEntity> cluster = new ArrayList<>();
                            clusterList.add(cluster);
                            cluster.add(changedBuilding);
                        }
                        else if (hostClusterPropertyEntityIDs.size() == 1)
                        { // there is one host cluster : add building to the cluster
                            int hostIndex = this.getClusterIndex(hostClusterPropertyEntityIDs.get(0));
                            clusterList.get(hostIndex).add(changedBuilding);
                        }
                        else
                        { // there are multiple host clusters : add building to the cluster & combine clusters
                            int hostIndex = this.getClusterIndex(hostClusterPropertyEntityIDs.get(0));
                            List<StandardEntity> hostCluster = clusterList.get(hostIndex);
                            hostCluster.add(changedBuilding);
                            for (int index = 1; index < hostClusterPropertyEntityIDs.size(); index++)
                            {
                                int tergetClusterIndex = this.getClusterIndex(hostClusterPropertyEntityIDs.get(index));
                                hostCluster.addAll(clusterList.get(tergetClusterIndex));
                                clusterList.remove(tergetClusterIndex);
                            }
                        }
                    }
                }
                else
                { // cE is contained in cluster
                    if (!(isBurning(changedBuilding)))
                    { // cE is not burning building
                        int hostClusterIndex = this.getClusterIndex(changedBuilding);
                        List<StandardEntity> hostCluster = clusterList.get(hostClusterIndex);

                        hostCluster.remove(changedBuilding);

                        if (hostCluster.isEmpty())
                        { // host cluster is empty
                            clusterList.remove(hostClusterIndex);
                        }
                        else
                        {
                            // update cluster
                            List<StandardEntity> relatedBuilding = new ArrayList<>();
                            relatedBuilding.addAll(hostCluster);
                            hostCluster.clear();

                            int clusterCount = 0;
                            while (!(relatedBuilding.isEmpty()))
                            {
                                if ((clusterCount++) > 0)
                                {
                                    List<StandardEntity> cluster = new ArrayList<>();
                                    clusterList.add(cluster);
                                    hostCluster = cluster;
                                }

                                List<StandardEntity> openedBuilding = new LinkedList<>();
                                openedBuilding.add(relatedBuilding.get(0));
                                hostCluster.add(relatedBuilding.get(0));
                                relatedBuilding.remove(0);

                                while (!(openedBuilding.isEmpty()))
                                {
                                    for (StandardEntity entity : relatedBuilding)
                                    {
                                        if (worldInfo.getDistance(openedBuilding.get(0), entity) <= groupingDistance)
                                        {
                                            openedBuilding.add(entity);
                                            hostCluster.add(entity);
                                        }
                                    }
                                    openedBuilding.remove(0);
                                    relatedBuilding.removeAll(openedBuilding);
                                }
                            }
                        }
                    }
                }
            }
        }
        return this;
    }

    @Override
    public Clustering updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() > 1) { return this; }

        this.calc(); // invoke calc()

        this.debugStdOut("Cluster : " + clusterList.size());

        return this;
    }

    @Override
    public Clustering precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() > 1) { return this; }
        return this;
    }

    @Override
    public Clustering resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if(this.getCountResume() > 1) { return this; }
        return this;
    }

    @Override
    public Clustering preparate()
    {
        super.preparate();
        if(this.getCountPreparate() > 1) { return this; }
        return this;
    }

    /**
     * 返回簇的数目
     */
    @Override
    public int getClusterNumber()
    {
        return clusterList.size();
    }

    /**
     * 返回standardEntity所在簇的id
     * 不在任何一个簇，返回-1
     */
    @Override
    public int getClusterIndex(StandardEntity standardEntity)
    {
        for (int index = 0; index < clusterList.size(); index++)
        {
            if (clusterList.get(index).contains(standardEntity))
            { return index; }
        }
        return -1;
    }

    /**
     * 返回entityID所指Entity所在簇的id
     */
    @Override
    public int getClusterIndex(EntityID entityID)
    {
        return getClusterIndex(worldInfo.getEntity(entityID));
    }

    /**
     * 返回第i个簇中所有Entity
     */
    @Override
    public Collection<StandardEntity> getClusterEntities(int i)
    {
        return clusterList.get(i);
    }

    /**
     * 返回第i个簇中所有Entity的id构成的集合
     */
    @Override
    public Collection<EntityID> getClusterEntityIDs(int i)
    {
        ArrayList<EntityID> list = new ArrayList<>();
        for (StandardEntity entity : getClusterEntities(i))
        { list.add(entity.getID()); }
        return list;
    }


    /**
     * classify burning building
     * @param building target building
     * @return is building burning
     */
    private boolean isBurning(Building building)
    {
        if (building.isFierynessDefined())
        {
            switch (building.getFieryness())
            {
                case 1: case 2: case 3:
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    /**
     * output text with class name to STDOUT when debug-mode.
     * @param text output text
     */
    private void debugStdOut(String text)
    {
        if (scenarioInfo.isDebugMode())
        { System.out.println("[" + this.getClass().getSimpleName() + "] " + text); }
    }
}