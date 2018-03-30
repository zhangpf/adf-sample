package adf.sample.module.algorithm;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.StaticClustering;
import rescuecore2.misc.Pair;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import java.util.*;

//import static java.util.Comparator.comparing;
//import static java.util.Comparator.reverseOrder;

/**
 * 完成对world中Entity的聚类
 * @author gxd
 */
public class SampleKMeans extends StaticClustering {
    private static final String KEY_CLUSTER_SIZE = "sample.clustering.size";
    private static final String KEY_CLUSTER_CENTER = "sample.clustering.centers";
    private static final String KEY_CLUSTER_ENTITY = "sample.clustering.entities.";
    private static final String KEY_ASSIGN_AGENT = "sample.clustering.assign";

    private int repeatPrecompute;
    private int repeatPreparate;

    /**
     * 所有实体
     */
    private Collection<StandardEntity> entities;

    /**
     * 簇中心集合
     */
    private List<StandardEntity> centerList;
    
    /**
     * 簇中心的ID的集合
     */
    private List<EntityID> centerIDs;
    
    /**
     * k个簇的具体信息
     */
    private Map<Integer, List<StandardEntity>> clusterEntitiesList;
    private List<List<EntityID>> clusterEntityIDsList;
    
    /**
     * 簇的个数
     */
    private int clusterSize;

    private boolean assignAgentsFlag;
    
    /**
     * 根据地图中的Area构造双向图
     * initShortestPath(WorldInfo worldInfo)方法中构造此信息
     */
    private Map<EntityID, Set<EntityID>> shortestPathGraph;

    public SampleKMeans(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.repeatPrecompute = developData.getInteger("sample.module.SampleKMeans.repeatPrecompute", 7);
        this.repeatPreparate = developData.getInteger("sample.module.SampleKMeans.repeatPreparate", 30);
        this.clusterSize = developData.getInteger("sample.module.SampleKMeans.clusterSize", 10);
        this.assignAgentsFlag = developData.getBoolean("sample.module.SampleKMeans.assignAgentsFlag", true);
        this.clusterEntityIDsList = new ArrayList<>();
        this.centerIDs = new ArrayList<>();
        this.clusterEntitiesList = new HashMap<>();
        this.centerList = new ArrayList<>();
        this.entities = wi.getEntitiesOfType(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
    }

    /**
     * 清除聚类的信息
     */
    @Override
    public Clustering updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.centerList.clear();
        this.clusterEntitiesList.clear();
        return this;
    }

    /**
     * 基于路径长度进行聚类，并将结果存储在precomputeData中
     * 存储的数据包括
     * this.clusterSize, this.centerIDs, this.clusterEntityIDsList, this.assignAgentFlag
     */
    @Override
    public Clustering precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.calcPathBased(this.repeatPrecompute);
        this.entities = null;
        // write
        precomputeData.setInteger(KEY_CLUSTER_SIZE, this.clusterSize);
        precomputeData.setEntityIDList(KEY_CLUSTER_CENTER, this.centerIDs);
        for(int i = 0; i < this.clusterSize; i++) {
            precomputeData.setEntityIDList(KEY_CLUSTER_ENTITY + i, this.clusterEntityIDsList.get(i));
        }
        precomputeData.setBoolean(KEY_ASSIGN_AGENT, this.assignAgentsFlag);
        return this;
    }
    
    /**
     * 从precomputeData中恢复聚类的信息
     * 恢复的数据包括
     * this.clusterSize, this.centerIDs, this.clusterEntityIDsList, this.assignAgentFlag
     */
    @Override
    public Clustering resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.entities = null;
        // read
        this.clusterSize = precomputeData.getInteger(KEY_CLUSTER_SIZE);
        this.centerIDs = new ArrayList<>(precomputeData.getEntityIDList(KEY_CLUSTER_CENTER));
        this.clusterEntityIDsList = new ArrayList<>(this.clusterSize);
        for(int i = 0; i < this.clusterSize; i++) {
            this.clusterEntityIDsList.add(i, precomputeData.getEntityIDList(KEY_CLUSTER_ENTITY + i));
        }
        this.assignAgentsFlag = precomputeData.getBoolean(KEY_ASSIGN_AGENT);
        return this;
    }

    /**
     * 基于直线距离进行聚类
     * 
     */
    @Override
    public Clustering preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.calcStandard(this.repeatPreparate);
        this.entities = null;
        return this;
    }

    /**
     * 返回簇的个数
     */
    @Override
    public int getClusterNumber() {
        //The number of clusters
        return this.clusterSize;
    }
    
    /**
     * 返回entity所在的簇
     */
    @Override
    public int getClusterIndex(StandardEntity entity) {
        return this.getClusterIndex(entity.getID());
    }
    
    /**
     * 返回id所指的Entity所在的簇
     */
    @Override
    public int getClusterIndex(EntityID id) {
        for(int i = 0; i < this.clusterSize; i++) {
            if(this.clusterEntityIDsList.get(i).contains(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 返回第index的簇包含的所有Entity
     */
    @Override
    public Collection<StandardEntity> getClusterEntities(int index) {
        List<StandardEntity> result = this.clusterEntitiesList.get(index);
        if(result == null || result.isEmpty()) {
            List<EntityID> list = this.clusterEntityIDsList.get(index);
            result = new ArrayList<>(list.size());
            for(int i = 0; i < list.size(); i++) {
                result.add(i, this.worldInfo.getEntity(list.get(i)));
            }
            this.clusterEntitiesList.put(index, result);
        }
        return result;
    }
    
    /**
     * 返回第index的簇包含的所有Entity的ID
     */
    @Override
    public Collection<EntityID> getClusterEntityIDs(int index) {
        return this.clusterEntityIDsList.get(index);
    }

    @Override
    public Clustering calc() {
        return this;
    }
    /**
     * 根据直线距离做聚类
     * @param repeat KMeans算法迭代的轮数
     */
    private void calcStandard(int repeat) {
        this.initShortestPath(this.worldInfo);
        Random random = new Random();

        List<StandardEntity> entityList = new ArrayList<>(this.entities);
        this.centerList = new ArrayList<>(this.clusterSize);
        this.clusterEntitiesList = new HashMap<>(this.clusterSize);

        //init list
        for (int index = 0; index < this.clusterSize; index++) {
            this.clusterEntitiesList.put(index, new ArrayList<>());
            this.centerList.add(index, entityList.get(0));
        }
        System.out.println("[" + this.getClass().getSimpleName() + "] Cluster : " + this.clusterSize);
        //init center
        for (int index = 0; index < this.clusterSize; index++) {
            StandardEntity centerEntity;
            do {
                centerEntity = entityList.get(Math.abs(random.nextInt()) % entityList.size());
            } while (this.centerList.contains(centerEntity));
            this.centerList.set(index, centerEntity);
        }
        //calc center
        for (int i = 0; i < repeat; i++) {
            this.clusterEntitiesList.clear();
            for (int index = 0; index < this.clusterSize; index++) {
                this.clusterEntitiesList.put(index, new ArrayList<>());
            }
            for (StandardEntity entity : entityList) {
                StandardEntity tmp = this.getNearEntityByLine(this.worldInfo, this.centerList, entity);
                this.clusterEntitiesList.get(this.centerList.indexOf(tmp)).add(entity);
            }
            for (int index = 0; index < this.clusterSize; index++) {
                int sumX = 0, sumY = 0;
                for (StandardEntity entity : this.clusterEntitiesList.get(index)) {
                    Pair<Integer, Integer> location = this.worldInfo.getLocation(entity);
                    sumX += location.first();
                    sumY += location.second();
                }
                int centerX = sumX / this.clusterEntitiesList.get(index).size();
                int centerY = sumY / this.clusterEntitiesList.get(index).size();
                StandardEntity center = this.getNearEntityByLine(this.worldInfo, this.clusterEntitiesList.get(index), centerX, centerY);
                if(center instanceof Area) {
                    this.centerList.set(index, center);
                }
                else if(center instanceof Human) {
                    this.centerList.set(index, this.worldInfo.getEntity(((Human) center).getPosition()));
                }
                else if(center instanceof Blockade) {
                    this.centerList.set(index, this.worldInfo.getEntity(((Blockade) center).getPosition()));
                }
            }
            if  (scenarioInfo.isDebugMode()) { System.out.print("*"); }
        }

        if  (scenarioInfo.isDebugMode()) { System.out.println(); }

        //set entity
        this.clusterEntitiesList.clear();
        for (int index = 0; index < this.clusterSize; index++) {
            this.clusterEntitiesList.put(index, new ArrayList<>());
        }
        for (StandardEntity entity : entityList) {
            StandardEntity tmp = this.getNearEntityByLine(this.worldInfo, this.centerList, entity);
            this.clusterEntitiesList.get(this.centerList.indexOf(tmp)).add(entity);
        }

        //this.clusterEntitiesList.sort(comparing(List::size, reverseOrder()));

        if(this.assignAgentsFlag) {
            List<StandardEntity> firebrigadeList = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));
            List<StandardEntity> policeforceList = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.POLICE_FORCE));
            List<StandardEntity> ambulanceteamList = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.AMBULANCE_TEAM));

            this.assignAgents(this.worldInfo, firebrigadeList);
            this.assignAgents(this.worldInfo, policeforceList);
            this.assignAgents(this.worldInfo, ambulanceteamList);
        }

        this.centerIDs = new ArrayList<>();
        for(int i = 0; i < this.centerList.size(); i++) {
            this.centerIDs.add(i, this.centerList.get(i).getID());
        }
        for (int index = 0; index < this.clusterSize; index++) {
            List<StandardEntity> entities = this.clusterEntitiesList.get(index);
            List<EntityID> list = new ArrayList<>(entities.size());
            for(int i = 0; i < entities.size(); i++) {
                list.add(i, entities.get(i).getID());
            }
            this.clusterEntityIDsList.add(index, list);
        }
    }
    
    /**
     * 根据路径距离做聚类
     * @param repeat KMeans算法迭代的轮数
     */
    private void calcPathBased(int repeat) {
    	//region 初始化
        this.initShortestPath(this.worldInfo);
        Random random = new Random();
        List<StandardEntity> entityList = new ArrayList<>(this.entities);
        this.centerList = new ArrayList<>(this.clusterSize);
        this.clusterEntitiesList = new HashMap<>(this.clusterSize);
        
        
        for (int index = 0; index < this.clusterSize; index++) {
            this.clusterEntitiesList.put(index, new ArrayList<>());
            this.centerList.add(index, entityList.get(0));
        }
        //随机选择clusterSize个簇中心
        for (int index = 0; index < this.clusterSize; index++) {
            StandardEntity centerEntity;
            do {
                centerEntity = entityList.get(Math.abs(random.nextInt()) % entityList.size());
            } while (this.centerList.contains(centerEntity));
            this.centerList.set(index, centerEntity);
        }
        //endregion
        
        //迭代repeat轮计算
        for (int i = 0; i < repeat; i++) {
        	//第i轮的初始化
            this.clusterEntitiesList.clear();
            for (int index = 0; index < this.clusterSize; index++) {
                this.clusterEntitiesList.put(index, new ArrayList<>());
            }
            //遍历每个Entity，将其加入距离其最近的簇中
            for (StandardEntity entity : entityList) {
                StandardEntity tmp = this.getNearEntity(this.worldInfo, this.centerList, entity);
                this.clusterEntitiesList.get(this.centerList.indexOf(tmp)).add(entity);
            }
            //重新计算簇的中心
            for (int index = 0; index < this.clusterSize; index++) {
                int sumX = 0, sumY = 0;
                for (StandardEntity entity : this.clusterEntitiesList.get(index)) {
                    Pair<Integer, Integer> location = this.worldInfo.getLocation(entity);
                    sumX += location.first();
                    sumY += location.second();
                }
                int centerX = sumX / clusterEntitiesList.get(index).size();
                int centerY = sumY / clusterEntitiesList.get(index).size();

                //this.centerList.set(index, getNearEntity(this.worldInfo, this.clusterEntitiesList.get(index), centerX, centerY));
                StandardEntity center = this.getNearEntity(this.worldInfo, this.clusterEntitiesList.get(index), centerX, centerY);
                if (center instanceof Area) {
                    this.centerList.set(index, center);
                } else if (center instanceof Human) {
                	//如果新的中心是Human，则将Human所在的Area当做中心
                    this.centerList.set(index, this.worldInfo.getEntity(((Human) center).getPosition()));
                } else if (center instanceof Blockade) {
                	//如果新的中心是Blockade，则将Blockade所在的Area当做中心
                    this.centerList.set(index, this.worldInfo.getEntity(((Blockade) center).getPosition()));
                }
            }
            if  (scenarioInfo.isDebugMode()) { System.out.print("*"); }
        }

        if  (scenarioInfo.isDebugMode()) { System.out.println(); }
        
        //region repeat轮次的迭代过后，将所有实体按照新的中心聚类
        this.clusterEntitiesList.clear();
        for (int index = 0; index < this.clusterSize; index++) {
            this.clusterEntitiesList.put(index, new ArrayList<>());
        }
        for (StandardEntity entity : entityList) {
            StandardEntity tmp = this.getNearEntity(this.worldInfo, this.centerList, entity);
            this.clusterEntitiesList.get(this.centerList.indexOf(tmp)).add(entity);
        }
        //endregion
        
        //this.clusterEntitiesList.sort(comparing(List::size, reverseOrder()));
        //this.entites初始化时不包括Agents，此段代码将三类Agent也加入聚类结果中
        if (this.assignAgentsFlag) {
            List<StandardEntity> fireBrigadeList = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));
            List<StandardEntity> policeForceList = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.POLICE_FORCE));
            List<StandardEntity> ambulanceTeamList = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.AMBULANCE_TEAM));
            this.assignAgents(this.worldInfo, fireBrigadeList);
            this.assignAgents(this.worldInfo, policeForceList);
            this.assignAgents(this.worldInfo, ambulanceTeamList);
        }
        
        //根据上面的聚类结果构造this.centerIDs与this.clusterEntityIDsList
        this.centerIDs = new ArrayList<>();
        for(int i = 0; i < this.centerList.size(); i++) {
            this.centerIDs.add(i, this.centerList.get(i).getID());
        }
        for (int index = 0; index < this.clusterSize; index++) {
            List<StandardEntity> entities = this.clusterEntitiesList.get(index);
            List<EntityID> list = new ArrayList<>(entities.size());
            for(int i = 0; i < entities.size(); i++) {
                list.add(i, entities.get(i).getID());
            }
            this.clusterEntityIDsList.add(index, list);
        }
    }
    
    /**
     * 将agentList中的所有agent根据this.centerList聚成类
     * @param world
     * @param agentList
     */
    private void assignAgents(WorldInfo world, List<StandardEntity> agentList) {
        int clusterIndex = 0;
        while (agentList.size() > 0) {
            StandardEntity center = this.centerList.get(clusterIndex);
            StandardEntity agent = this.getNearAgent(world, agentList, center);
            this.clusterEntitiesList.get(clusterIndex).add(agent);
            agentList.remove(agent);
            clusterIndex++;
            if (clusterIndex >= this.clusterSize) {
                clusterIndex = 0;
            }
        }
    }
    
    /**
     * 计算srcEntityList中距离targetEntity的Location-(X, Y)最近的Entity
     * 根据直线距离计算
     * @param world
     * @param srcEntityList
     * @param targetEntity
     * @return
     */
    private StandardEntity getNearEntityByLine(WorldInfo world, List<StandardEntity> srcEntityList, StandardEntity targetEntity) {
        Pair<Integer, Integer> location = world.getLocation(targetEntity);
        return this.getNearEntityByLine(world, srcEntityList, location.first(), location.second());
    }

    /**
     * 计算srcEntityList中距离(targetX, targetY)最近的Entity
     * 根据Entity到(targetX, targetY)的直线距离计算
     * @param world
     * @param srcEntityList
     * @param targetX
     * @param targetY
     * @return
     */
    private StandardEntity getNearEntityByLine(WorldInfo world, List<StandardEntity> srcEntityList, int targetX, int targetY) {
        StandardEntity result = null;
        for(StandardEntity entity : srcEntityList) {
            result = ((result != null) ? this.compareLineDistance(world, targetX, targetY, result, entity) : entity);
        }
        return result;
    }

    /**
     * 返回srcAgentList中距离targetEntity最近的Agent，
     * 根据Agent所在的Entity（building,road等）到targetEntity的路径距离计算
     * @param worldInfo
     * @param srcAgentList
     * @param targetEntity
     * @return
     */
    private StandardEntity getNearAgent(WorldInfo worldInfo, List<StandardEntity> srcAgentList, StandardEntity targetEntity) {
        StandardEntity result = null;
        for (StandardEntity agent : srcAgentList) {
            Human human = (Human)agent;
            if (result == null) {
                result = agent;
            }
            else {
                if (this.comparePathDistance(worldInfo, targetEntity, result, worldInfo.getPosition(human)).equals(worldInfo.getPosition(human))) {
                    result = agent;
                }
            }
        }
        return result;
    }
    
    /**
     * 从srcEntityList中返回距离(targetX, targetY)最近的entity
     * 根据点到Entity的直线距离计算
     * @param worldInfo
     * @param srcEntityList
     * @param targetX
     * @param targetY
     * @return
     */
    private StandardEntity getNearEntity(WorldInfo worldInfo, List<StandardEntity> srcEntityList, int targetX, int targetY) {
        StandardEntity result = null;
        for (StandardEntity entity : srcEntityList) {
            result = (result != null) ? this.compareLineDistance(worldInfo, targetX, targetY, result, entity) : entity;
        }
        return result;
    }
    
    /**
     * 获得线段的中点
     * @param edge
     * @return
     */
    private Point2D getEdgePoint(Edge edge) {
        Point2D start = edge.getStart();
        Point2D end = edge.getEnd();
        return new Point2D(((start.getX() + end.getX()) / 2.0D), ((start.getY() + end.getY()) / 2.0D));
    }


    private double getDistance(double fromX, double fromY, double toX, double toY) {
        double dx = fromX - toX;
        double dy = fromY - toY;
        return Math.hypot(dx, dy);
    }
    
    private double getDistance(Pair<Integer, Integer> from, Point2D to) {
        return getDistance(from.first(), from.second(), to.getX(), to.getY());
    }
    
    /**
     * 点到线段中点的距离
     * @param from
     * @param to
     * @return
     */
    private double getDistance(Pair<Integer, Integer> from, Edge to) {
        return getDistance(from, getEdgePoint(to));
    }

    private double getDistance(Point2D from, Point2D to) {
        return getDistance(from.getX(), from.getY(), to.getX(), to.getY());
    }
    
    /**
     * 两个线段中点之间的距离
     * @param from
     * @param to
     * @return
     */
    private double getDistance(Edge from, Edge to) {
        return getDistance(getEdgePoint(from), getEdgePoint(to));
    }
    
    /**
     * 返回两个Entity--first与second中到(targetX, targetY)直线距离小的那个Entity
     * @param worldInfo
     * @param targetX
     * @param targetY
     * @param first
     * @param second
     * @return
     */
    private StandardEntity compareLineDistance(WorldInfo worldInfo, int targetX, int targetY, StandardEntity first, StandardEntity second) {
        Pair<Integer, Integer> firstLocation = worldInfo.getLocation(first);
        Pair<Integer, Integer> secondLocation = worldInfo.getLocation(second);
        double firstDistance = getDistance(firstLocation.first(), firstLocation.second(), targetX, targetY);
        double secondDistance = getDistance(secondLocation.first(), secondLocation.second(), targetX, targetY);
        return (firstDistance < secondDistance ? first : second);
    }
    
    /**
     * 从srcEntityList中返回距离targetEntity最近的entity
     * 根据路径长度计算
     * @param worldInfo
     * @param srcEntityList
     * @param targetEntity
     * @return
     */
    private StandardEntity getNearEntity(WorldInfo worldInfo, List<StandardEntity> srcEntityList, StandardEntity targetEntity) {
        StandardEntity result = null;
        for (StandardEntity entity : srcEntityList) {
            result = (result != null) ? this.comparePathDistance(worldInfo, targetEntity, result, entity) : entity;
        }
        return result;
    }
    
    /**
     * 记target到first的边数最少的路径为path1，其长度为dis1
     * 记target到second的边数最少的路径为path2，其长度为dis2
     * 返回min(dis1, dis2)对应的Entity
     * @param worldInfo
     * @param target
     * @param first
     * @param second
     * @return
     */
    private StandardEntity comparePathDistance(WorldInfo worldInfo, StandardEntity target, StandardEntity first, StandardEntity second) {
        double firstDistance = getPathDistance(worldInfo, shortestPath(target.getID(), first.getID()));
        double secondDistance = getPathDistance(worldInfo, shortestPath(target.getID(), second.getID()));
        return (firstDistance < secondDistance ? first : second);
    }
    
    /**
     * 计算路径长度
     * 第1个Area和最后一个Area只计算半个的长度
     * @param worldInfo
     * @param path
     * @return
     */
    private double getPathDistance(WorldInfo worldInfo, List<EntityID> path) {
        if (path == null) return Double.MAX_VALUE;
        if (path.size() <= 1) return 0.0D;

        double distance = 0.0D;
        int limit = path.size() - 1;
        
        Area area = (Area)worldInfo.getEntity(path.get(0));
        distance += getDistance(worldInfo.getLocation(area), area.getEdgeTo(path.get(1)));
        area = (Area)worldInfo.getEntity(path.get(limit));
        distance += getDistance(worldInfo.getLocation(area), area.getEdgeTo(path.get(limit - 1)));
        
        //第i个格子的中心到(i)与(i-1)相交的edge的中点的距离 + 第i个格子的中心到(i)与(i+1)相交的edge的中点的距离
        for(int i = 1; i < limit; i++) {
            area = (Area)worldInfo.getEntity(path.get(i));
            distance += getDistance(area.getEdgeTo(path.get(i - 1)), area.getEdgeTo(path.get(i + 1)));
        }
        return distance;
    }

    
    /**
     * 根据world中的Area构造图，存储在this.shortestPathGraph中
     * @param worldInfo
     */
    private void initShortestPath(WorldInfo worldInfo) {
        Map<EntityID, Set<EntityID>> neighbours = new LazyMap<EntityID, Set<EntityID>>() {
            @Override
            public Set<EntityID> createValue() {
                return new HashSet<>();
            }
        };
        //正向构造邻接表
        for (Entity next : worldInfo) {
            if (next instanceof Area) {
                Collection<EntityID> areaNeighbours = ((Area) next).getNeighbours();
                neighbours.get(next.getID()).addAll(areaNeighbours);
            }
        }
        /*确保图是双向的
          如上一步有a->b, a->c
          此步加入边b->a, c->a
          理论上不是必须，可以保证构造的双向图没有问题
        */
        for (Map.Entry<EntityID, Set<EntityID>> graph : neighbours.entrySet()) {// fix graph
            for (EntityID entityID : graph.getValue()) {
                neighbours.get(entityID).add(graph.getKey());
            }
        }
        this.shortestPathGraph = neighbours;
    }

    private List<EntityID> shortestPath(EntityID start, EntityID... goals) {
        return shortestPath(start, Arrays.asList(goals));
    }

    /**
     * 找到start到goals集合最近的一条路径（能到达goals集合中任意一点即可）
     * “最近”的定义为路径上边的数量最少
     * @param start
     * @param goals
     * @return 最短的一条路径
     */
    private List<EntityID> shortestPath(EntityID start, Collection<EntityID> goals) {
        List<EntityID> open = new LinkedList<>();
        Map<EntityID, EntityID> ancestors = new HashMap<>();
        open.add(start);
        EntityID next;
        boolean found = false;
        ancestors.put(start, start);
        do {
            next = open.remove(0);
            if (isGoal(next, goals)) {
                found = true;
                break;
            }
            Collection<EntityID> neighbours = shortestPathGraph.get(next);
            if (neighbours.isEmpty()) continue;

            for (EntityID neighbour : neighbours) {
                if (isGoal(neighbour, goals)) {
                    ancestors.put(neighbour, next);
                    next = neighbour;
                    found = true;
                    break;
                }
                else if (!ancestors.containsKey(neighbour)) {
                    open.add(neighbour);
                    ancestors.put(neighbour, next);
                }
            }
        } while (!found && !open.isEmpty());
        if (!found) {
            // No path
            return null;
        }
        // Walk back from goal to start
        EntityID current = next;
        List<EntityID> path = new LinkedList<>();
        do {
            path.add(0, current);
            current = ancestors.get(current);
            if (current == null) throw new RuntimeException("Found a node with no ancestor! Something is broken.");
        } while (current != start);
        return path;
    }

    private boolean isGoal(EntityID e, Collection<EntityID> test) {
        return test.contains(e);
    }
}
