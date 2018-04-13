package NUDT.extaction;

import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.ode.nonstiff.ThreeEighthesIntegrator;
import org.jaxen.util.SelfAxisIterator;

import NUDT.module.DynamicInfoContainer.CriticalArea;
import NUDT.module.DynamicInfoContainer.PoliceForceHelpInfos;
import NUDT.module.DynamicInfoContainer.PosAndLocHistory;
import NUDT.module.DynamicInfoContainer.StuckedAgents;
import NUDT.module.StaticInfoContainer.StaticInfoContainerModule;
import NUDT.module.algorithm.pathplanning.pov.POVRouter;
import NUDT.utils.extendTools.EntityTools;
import NUDT.utils.extendTools.RoadTools;
import NUDT.utils.extendTools.WorldTools;
import NUDT.utils.extendTools.AgentTools;
import NUDT.utils.Ruler;
import NUDT.utils.Util;

import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.StandardMessageURN;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.misc.Pair;

import adf.agent.action.common.ActionMove;
import adf.agent.action.police.ActionClear;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import csu.agent.Agent.ActionCommandException;
import csu.model.AgentConstants;
import NUDT.module.algorithm.pathplanning.pov.CostFunction;
import NUDT.utils.extendTools.PoliceForceTools.PFLastTaskType;
import NUDT.utils.extendTools.PoliceForceTools.PFLastTaskType.PFClusterLastTaskEnum;
import NUDT.utils.extendTools.PoliceForceTools.PoliceForceTools;


/**
 * from POSBased，
 * 完成detect目标、并计算出要采取的行动，
 * 相当于合并了原先的TargetDetector和ExtAction
 * @author gxd
 *
 */
public class ActionExtClear extends ExtAction
{
	protected List<EntityID> lastCyclePath;
	
	protected int x, y, time;
	
	protected double repairDistance;
	
	protected int lastClearDest_x = -1, lastClearDest_y = -1;
	protected int count = 0, lock = 4, reverseLock = 4;
	
	//Dynamic Infos
	protected CriticalArea criticalArea;
	protected PoliceForceHelpInfos policeForceHelpInfos;
	protected PosAndLocHistory posAndLocHistory;
	protected POVRouter router;
	
	protected Random random;
	//Static Infos
	protected StaticInfoContainerModule staticInfoContainerModule;
	
	public ActionExtClear(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData, CriticalArea ca, PoliceForceHelpInfos pfhis, StaticInfoContainerModule sicm,
			PosAndLocHistory polh, POVRouter router) {
		super(ai, wi, si, moduleManager, developData);
		this.criticalArea = ca;
		this.policeForceHelpInfos = pfhis;
		this.staticInfoContainerModule = sicm;
		this.posAndLocHistory = polh;
		this.router = router;
		
		this.time = this.agentInfo.getTime();
		
		this.random = new Random();
		
		Pair<Integer, Integer> selfLocPair = this.worldInfo.getLocation(this.agentInfo.me().getID());
		this.x = selfLocPair.first();
		this.y = selfLocPair.second();
		
		this.repairDistance = this.scenarioInfo.getClearRepairDistance();
	}
	
	public void updateClearPath(List<EntityID> path) {
		this.lastCyclePath = path;
		
		this.time = this.agentInfo.getTime();
		
		Pair<Integer, Integer> selfLocPair = this.worldInfo.getLocation(this.agentInfo.me().getID());
		this.x = selfLocPair.first();
		this.y = selfLocPair.second();
	}
	
	@Override
	public ExtAction setTarget(EntityID targets) {
		return this;
	}

	@Override
	public ExtAction calc() {
		
		this.cannotClear();
		this.careSelf();

		// 自己被困
		// 连续发送move，但是位置变化小。包括了困在building中
		if (isBlocked()) {

			Blockade target = this.blockedClear();
			if (target != null) {
				//pf没有用到lastMovePlan，此属性为空
				//this.updateClearPath(lastMovePlan);
				this.updateClearPath(new ArrayList<EntityID>());
				this.result = new ActionClear(target.getX(), target.getY());
				return this;
			}

			randomWalk();
		}

		// 自己locate in blockade
		// 应该提前
		this.stuckedClear();

		// 优先考虑周围情况
		this.coincidentWork();

		this.updateClearPath(lastCyclePath);
		this.clear();
		// 继续上次任务
		this.continueLastTask();
		this.traversalRefuge();

		this.helpBuriedAgent();
		this.helpStuckAgent();
		// 更替
		// this.searchingBurningBuilding();
		this.searchingBurningBuildingCritical();
		// this.traversalCritical();
		this.helpBuriedHumans();
		this.traversalEntrance();
		this.expandCluster();
		this.randomWalk();
		

		
		return this;
	}
	
	public void clear() {

		if (lastCyclePath == null)
			return;

		mixingClear();
	}
	
	
	/**
	 * 如果HP过低，就向refugee移动
	 * @throws csu.agent.Agent.ActionCommandException
	 *             不能够clear的pf，其实什么都作不了
	 */
	private void cannotClear(){
		Human me = (Human)this.agentInfo.me();
		if (!me.isHPDefined() || me.getHP() <= 1000) {
			this.policeForceHelpInfos.setClusterLastTaskType(PFClusterLastTaskEnum.CANNOT_TO_CLEAR);
			Collection<StandardEntity> allReguge = this.worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE);

			CostFunction costFunc = router.getNormalCostFunction();
			Point selfL = new Point(me.getX(), me.getY());
			
			StandardEntity myPos = AgentTools.selfPosition(this.agentInfo, this.worldInfo);
			//如果该agent在ambulanceTeam上，则返回null
			Area myPosArea = myPos instanceof Area ? (Area)myPos : null;
			lastCyclePath = router.getMultiAStar(myPosArea, 
					allReguge, costFunc, selfL);


			move(lastCyclePath);
		}
	}
	
	
	
	
	/**
	 * When the HP point of mine is less than a certain point, I need to go to refuge and rest.
	 * 
	 * @throws ActionCommandException
	 */
	protected void careSelf() {
		Human me = EntityTools.getHuman(this.agentInfo.me().getID(), this.worldInfo);
		if (me.getHP() - me.getDamage() * (this.scenarioInfo.getKernelTimesteps() - time) < 16) {
			moveToRefuge();
		}
	}
	
	/** Move to refuge.*/
	protected void moveToRefuge() {
		Collection<StandardEntity> refuges = this.worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE);
		
		if (refuges.isEmpty())
			return;
		
		move(refuges);
	}
	
	public void move(Collection<? extends StandardEntity> destinations) {
		move(destinations, router.getNormalCostFunction());
	}
	
	/**
	 * Find the best target location from a collection of destinations and move
	 * to this best target location with the given CostFunction.
	 * 
	 * @param destinations
	 *            a collection of destinations
	 * @param costFunc
	 *            the given CostFunction
	 * @throws ActionCommandException
	 */
	public void move(Collection<? extends StandardEntity> destinations,
			CostFunction costFunc){
		
		Pair<Integer, Integer> myLoc = this.worldInfo.getLocation(this.agentInfo.me());
		
		move(router.getMultiDest(
				EntityTools.getArea(AgentTools.selfPosition(this.agentInfo, this.worldInfo).getID(), this.worldInfo),
				destinations, costFunc, 
				new Point(myLoc.first(), myLoc.second())));
	}
	
	public void move(List<EntityID> path) {
		if (path.size() > 2) {
			EntityID id_1 = path.get(0);
			EntityID id_2 = path.get(1);
			if (id_1.getValue() == id_2.getValue())
				path.remove(0);
		}
		this.updateClearPath(path);
		this.clear();
		this.result = new ActionMove(path);
		this.lastCyclePath = null;
		return ;
	}
	
	public void move(List<EntityID> path, int destX, int destY) {
		if (path.size() > 2) {
			EntityID id_1 = path.get(0);
			EntityID id_2 = path.get(1);
			if (id_1.getValue() == id_2.getValue())
				path.remove(0);
		}

		this.updateClearPath(path);
		this.clear();
		this.result = new ActionMove(path, destX, destY);
		this.lastCyclePath = null;
		return ;
	}
	
	/** 
	 * 判断一个platoon agent是否被路障挡住。这种情况下，agent不能按照原来的路径继续前进，但可以后退。
	 * using two judging methods: the located roads need to cleared; the position varies little.
	 * @return 当一个platoon agent被路障挡住时返回true。否则，false。
	 */
	public boolean isBlocked() {
		//？？
		if (time < this.scenarioInfo.getKernelAgentsIgnoreuntil() + 2)
			return false;
		
		
		if (this.agentInfo.getExecutedAction(time-1).getCommand(this.agentInfo.me().getID(), time-1).getURN().equals(StandardMessageURN.AK_MOVE) &&
				this.agentInfo.getExecutedAction(time-2).getCommand(this.agentInfo.me().getID(), time-2).getURN().equals(StandardMessageURN.AK_MOVE)) {
			
			//201409
			EntityID position_id = this.posAndLocHistory.getHistoryPosition(time);
			StandardEntity position_entity = this.worldInfo.getEntity(position_id);
			if (position_entity instanceof Building) {
				
				Building position_building = (Building) position_entity;
				Set<Road> entranceList = this.staticInfoContainerModule.getEntrance().getEntrance(position_building);
				for (Road entrance : entranceList) {
					if (RoadTools.isNeedlessToClear(entrance, this.worldInfo)) {
						return false;
					}
				}
				return true;
				
			}
			
			Pair<Integer, Integer> location_1 = this.posAndLocHistory.getHistoryLocation(time - 2);
			Pair<Integer, Integer> location_2 = this.posAndLocHistory.getHistoryLocation(time - 1);
			Pair<Integer, Integer> location_3 = this.posAndLocHistory.getHistoryLocation(time);
			
			if (location_1 == null || location_2 == null || location_3 == null)
				return false;
			
			double distance_1 = Ruler.getDistance(location_1, location_2);
			double distance_2 = Ruler.getDistance(location_2, location_3);
			
			if (distance_1 < 8000 && distance_2 < 8000) {
				return true;
			}
		}
		
		return false;
	}
	
	protected void randomWalk() { ///

			Collection<StandardEntity> inRnage = this.worldInfo.getObjectsInRange(this.agentInfo.me(), 50000);
			List<Area> target = new ArrayList<>();
			Collection<Area> blockadeDefinedArea = new ArrayList<>();
			
			for (StandardEntity entity : inRnage) {
				if (!(entity instanceof Area))
					continue;
				if (entity.getID().getValue() == 
						AgentTools.selfAreaPosition(this.agentInfo, this.worldInfo).getID().getValue())
					continue;
				Area nearArea = (Area) entity;
				///now the blockadeDefinedArea is empty, exclude the blocked at first
				if (nearArea.isBlockadesDefined() && nearArea.getBlockades().size() > 0) {
					blockadeDefinedArea.add(nearArea);
					continue;
				}
				///would not go to the fire building
				if(entity instanceof Building && ((Building) entity).isOnFire()) 
					continue;
				
				target.add(nearArea);
			}
			
			if (target.isEmpty())
				target.addAll(blockadeDefinedArea);
			
			///randomCount = (randomCount < target.size()) ? randomCount++ : 0;///walk not randomly
			///Area randomTarget = target.get(randomCount);
			Area randomTarget = target.get(random.nextInt(target.size()));
			
			Pair<Integer, Integer> myLoc = this.worldInfo.getLocation(this.agentInfo.me());
			///System.out.println(time + ", " + me() + ", " + target + ",,,,,,,," + randomTarget);
			List<EntityID> path = router.getAStar(AgentTools.selfAreaPosition(this.agentInfo, this.worldInfo), 
					randomTarget, router.getNormalCostFunction(), 
					new Point(myLoc.first(), myLoc.second()));
				
			move(path);
		}
	
	private void stuckedClear() {
		Human me = EntityTools.getHuman(this.agentInfo.me().getID(), this.worldInfo);
		if(me == null) return ;
		if (PoliceForceTools.judgeStuck(me, this.worldInfo, this.staticInfoContainerModule.getEntrance())) {
			Blockade blockade = PoliceForceTools.isLocateInBlockade(me, this.worldInfo);
			if (blockade != null) {
				
				this.result = new ActionClear(blockade);
				return ;
			}
		}
	}
	
	/**
	 * @throws ActionCommandException
	 *             优先考虑周围偶然情况
	 */
	private void coincidentWork() {

		coincidentTaskUpdate();
		//201410
		clearEntranceForCivilian();
		//
		coincidentCheckRefuge();
		coincidentHelpStuckedAgent();
		coincidentHelpBuriedAgent();
		
		
	}
	
	
	/**
	 * 偶然任务更新 refuge and coincidentBuriedAgent agent in building
	 */
	private void coincidentTaskUpdate() {
		StandardEntity me = this.agentInfo.me();
		// 周围
		for (StandardEntity next : this.worldInfo.getObjectsInRange(me.getID(), 50000)) {
			if (next instanceof Refuge) {
				if (this.policeForceHelpInfos.getVisitedRefuges().contains(next.getID())) {
					this.policeForceHelpInfos.getCoincidentRefuges().remove((Refuge) next);
					continue;
				}
				this.policeForceHelpInfos.getCoincidentRefuges().add((Refuge) next);

				// buried agent in building
			} else if (next instanceof Human && !(next instanceof Civilian)) {
				if (this.policeForceHelpInfos.getVisitedBuriedAgent().contains(next.getID())) {
					this.policeForceHelpInfos.getCoincidentBuriedAgent().remove((Human) next);
					continue;
				}

				Human human = (Human) next;
				if (human.isBuriednessDefined() && human.getBuriedness() > 0
						&& human.isPositionDefined()) {
					StandardEntity loca = this.worldInfo.getPosition(human);
					if (loca instanceof Building) {
						this.policeForceHelpInfos.getCoincidentBuriedAgent().add(human);
					}
				}
			}
		}
	}
	
	private void clearEntranceForCivilian() {
		List<EntityID> buildingList = new ArrayList<>();
		List<EntityID> humanList = new ArrayList<>();
		for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
			StandardEntity entity = this.worldInfo.getEntity(next);
			if (entity instanceof Building) {
				buildingList.add(next);
			}else if (entity instanceof Human) {
				humanList.add(next);
			}
		}
		for (EntityID buildingID : buildingList ) {
			Building building =(Building) this.worldInfo.getEntity(buildingID);
			for (EntityID humanID : humanList) {
				Human human = (Human) this.worldInfo.getEntity(humanID);
				EntityID humanPositionID = human.getPosition();
				if (humanPositionID.getValue() == building.getID().getValue()) {
					Set<Road> entrances = this.staticInfoContainerModule.getEntrance().getEntrance(building);
					for (Road entrance : entrances) {
						if (entrance.isBlockadesDefined() && entrance.getBlockades().size() > 0) {
							double dis = Ruler.getDistance(this.worldInfo.getLocation(entrance.getID()), this.worldInfo.getLocation(this.agentInfo.me()));
							if (dis < 10000) {
								move(router.getAStar((Human)this.agentInfo.me(), (Area)entrance, router.getPfCostFunction()));
							}
						}
					}
				}
			}
		}
	}

	/**
	 * @throws ActionCommandException
	 *             move to coincidentCheckRefuge
	 */
	private void coincidentCheckRefuge() {
		if (this.policeForceHelpInfos.getCoincidentRefuges().size() > 0) {
			CostFunction costFunc = router.getPfCostFunction();
			Pair<Integer, Integer> myLoc = this.worldInfo.getLocation(this.agentInfo.me());
			Point selfL = new Point(myLoc.first(), myLoc.second());
			lastCyclePath = router.getMultiDest(AgentTools.selfAreaPosition(this.agentInfo, this.worldInfo), 
					this.policeForceHelpInfos.getCoincidentRefuges(),
					costFunc, selfL);

				EntityID destination = lastCyclePath
						.get(lastCyclePath.size() - 1);

			move(lastCyclePath);
		}
	}

	private void coincidentHelpStuckedAgent() {
		Pair<Integer, Integer> myLoc = this.worldInfo.getLocation(this.agentInfo.me());
		
		List<EntityID> needClearAgentIDList = getAgentOfCoincidentHelpStuckedAfent();
		Human closetAgent = null;
		double minDistance = Double.MAX_VALUE;
		for (EntityID entityID : needClearAgentIDList) {
			if (!this.worldInfo.getChanged().getChangedEntities().contains(entityID)) {
				return;
			}
			Human agent = (Human) this.worldInfo.getEntity(entityID);
			StandardEntity agentPosition = this.worldInfo.getEntity(agent.getPosition());
			if (agentPosition instanceof Building && (agent.isBuriednessDefined() && agent.getBuriedness() > 0)) {

				continue;
			}

			double dis = Ruler.getDistance(this.worldInfo.getLocation(agent), myLoc);
			if (dis < minDistance) {
				minDistance = dis;
				closetAgent = agent;
			}
		}

		if (closetAgent != null) {
			
			StandardEntity entity = this.worldInfo.getEntity(closetAgent.getPosition());
			if (!(entity instanceof Area))
				return;
			Area agentLocation = (Area) entity;
			
			if (agentLocation instanceof Building) {
				Building building = (Building) agentLocation;
				Set<Road> entrancesOfStuckAgent = this.staticInfoContainerModule.getEntrance().getEntrance(building);
				traversalRoads(entrancesOfStuckAgent);

				return;
			}

			CostFunction costFunc = router.getPfCostFunction();
			Point selfL = new Point(myLoc.first(), myLoc.second());
			lastCyclePath = router.getAStar(AgentTools.selfAreaPosition(this.agentInfo, this.worldInfo),
					agentLocation, costFunc, selfL);

			move(lastCyclePath, closetAgent.getX(), closetAgent.getY());
		}
	}
	
	public List<EntityID> getAgentOfCoincidentHelpStuckedAfent() {
		List<EntityID> needClearAgent = new ArrayList<>();
		// changeset 中的at pf，
		List<EntityID> inChangeSetAT_FB = new ArrayList<>();
		// blockade
		List<EntityID> inChangeSetBlockades = new ArrayList<>();

		for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
			StandardEntity entity = this.worldInfo.getEntity(next);
			if (entity instanceof AmbulanceTeam
					|| entity instanceof FireBrigade) {
				inChangeSetAT_FB.add(next);
			} else if (entity instanceof Blockade) {
				inChangeSetBlockades.add(next);
			}
		}
		
		for (EntityID agent_id : inChangeSetAT_FB) {
			Human agent = (Human) this.worldInfo.getEntity(agent_id);
			for (EntityID blockade_id : inChangeSetBlockades) {
				Blockade blockade = (Blockade) this.worldInfo.getEntity(blockade_id);
				double dis = Ruler.getDistanceToBlock(blockade,
						new Point(agent.getX(), agent.getY()));
				if (dis < 500) {
					needClearAgent.add(agent_id);
				}
			}

		}
		
		needClearAgent.addAll(this.policeForceHelpInfos.getStuckedAgents());
		return needClearAgent;
	}
	
	private void traversalRoads(Set<Road> roads) {
		Pair<Integer, Integer> myLoc = this.worldInfo.getLocation(this.agentInfo.me());

		if (roads.size() == 0) {
			return;
		}

		CostFunction costFunc = router.getPfCostFunction();
		Point selfL = new Point(myLoc.first(), myLoc.second());
		lastCyclePath = router.getMultiAStar(AgentTools.selfAreaPosition(this.agentInfo, this.worldInfo), 
				roads, costFunc, selfL);
		Road destination = (Road) this.worldInfo.getEntity(lastCyclePath
				.get(lastCyclePath.size() - 1));

		roads.remove(destination);
		this.policeForceHelpInfos.getTraversalEntranceSet().remove(destination);

		this.policeForceHelpInfos.setClusterLastTaskType(PFClusterLastTaskEnum.TRAVERSAL_ENTRANCE);
		this.policeForceHelpInfos.getTaskTarget().setTraversalEntrance(destination);
		this.policeForceHelpInfos.getTaskTarget().setEntlityList(lastCyclePath);


		move(lastCyclePath);
	}
	
	/**
	 * @throws ActionCommandException
	 *             move to buiried agent ‘s entrance
	 */
	private void coincidentHelpBuriedAgent() {
		Pair<Integer, Integer> myLoc = this.worldInfo.getLocation(this.agentInfo.me());
		
		if (this.policeForceHelpInfos.getCoincidentBuriedAgent().size() > 0) {
			CostFunction costFunc = router.getPfCostFunction();
			Point selfL = new Point(myLoc.first(), myLoc.second());

			List<StandardEntity> dest = new ArrayList<>();

			for (Human next : this.policeForceHelpInfos.getCoincidentBuriedAgent()) {
				StandardEntity loca = this.worldInfo.getPosition(next);
				if (loca instanceof Building) {
					dest.addAll(this.staticInfoContainerModule.getEntrance()
							.getEntrance((Building) loca));
				}
			}

			lastCyclePath = router.getMultiDest(AgentTools.selfAreaPosition(this.agentInfo, this.worldInfo), 
					dest, costFunc, selfL);

			move(lastCyclePath);
		}
	}
	
	
	
	/* (non-Javadoc)
	 * @see csu.agent.pf.clearStrategy.I_ClearStrategy#blockedClear()
	 * 返回新发现的并且能"够到"的所有blockade中距离自己最近的blockade
	 */
	public Blockade blockedClear() {
		Set<Blockade> blockades = new HashSet<>();
		StandardEntity entity = null;
		for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
			entity = this.worldInfo.getEntity(next);
			
			if (entity instanceof Blockade) {
				Blockade bloc = (Blockade) entity;
				//小于3个点的路障不能围成多边形，所以忽略
				if (bloc.isApexesDefined() && bloc.getApexes().length < 6)
					continue;
				blockades.add(bloc);
			}
		}
		
		Blockade nearestBlockade = null;
		double minDistance = repairDistance;
		for (Blockade next : blockades) {
			double distance = AgentTools.getDistanceToBlockade(next, x, y);
			if (distance < minDistance) {
				nearestBlockade = next;
				minDistance = distance;
			}
		}
		
		return nearestBlockade;
	}
	

	/**
	 * @throws ActionCommandException
	 */
	public void mixingClear() {

		boolean needClear = true;
		//path最后一个是road不是入口，critical area ，以及在 changeset 中

		if (needClear == false) {
			lastCyclePath = null;
			return;
		}

		// 自己的坐标
		Point2D selfL = new Point2D(x, y);
		int pathLength = lastCyclePath.size();	//路线中的道路数
		// 所在的road，假如被at load ？？ 或者在building
		Road road = EntityTools.getRoad(EntityTools.getSelfPosition(this.agentInfo, this.worldInfo).getID(), this.worldInfo);//身处的道路
		/**
		 * agent自己所在的Area
		 */
		StandardEntity cur_entity = EntityTools.getSelfPosition(this.agentInfo, this.worldInfo);
		// 不在area，可能在at
		if (!(cur_entity instanceof Area))
			return;
		//agent自己所在的Area
		Area currentArea = (Area) cur_entity;

		// 最近的blockade
		double minDistance = Double.MAX_VALUE;
		Blockade nearestBlockade = null;

		int indexInPath = findIndexInPath(lastCyclePath, cur_entity.getID());

		// 下标会等于大小？？
		
		if (indexInPath == pathLength) {
			//System.out.println("0");
			return;
		} else if (indexInPath == (pathLength - 1)) {
			//System.out.println("1");
			if (currentArea instanceof Road
					&& road.isBlockadesDefined()) {
				
				//如果自己不在某个建筑的entrance并且不在criticalArea
				if (!this.staticInfoContainerModule.getEntrance().isEntrance(road)
					&& !this.criticalArea.isCriticalArea(cur_entity.getID())) {
					// changeset 中的at pf，
					//观察到的救护员与消防员
					List<EntityID> inChangeSetAT_FB = new ArrayList<>();
					// blockade
					//观察到的blockades
					List<EntityID> inChangeSetBlockades = new ArrayList<>();

					
					for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
						StandardEntity entity = this.worldInfo.getEntity(next);
						if (entity instanceof AmbulanceTeam
								|| entity instanceof FireBrigade) {
							inChangeSetAT_FB.add(next);
						} else if (entity instanceof Blockade) {
							inChangeSetBlockades.add(next);
						}
					}
					/**
					 * 视野内需要被救援的agents
					 * 如果视野内的消防员与救护员被blockade困住，则将这部分agent加入到needClearAgent中
					 */
					HashSet<EntityID> needClearAgent = new HashSet<>();
					//System.out.println("4");
					for (EntityID agent_id : inChangeSetAT_FB) {
						Human agent = (Human) this.worldInfo.getEntity(agent_id);
						for (EntityID blockade_id : inChangeSetBlockades) {
							Blockade blockade = (Blockade) this.worldInfo.getEntity(blockade_id);
							double dis = Ruler.getDistanceToBlock(blockade,
									new Point(agent.getX(), agent.getY()));
							if (dis < 2000) {
								needClearAgent.add(agent_id);
							}
						}
					}
					
					//被困住的agent如果与自己在同一个area，则将这部分agent加入到needClearAgent中
					for (EntityID entityID : this.policeForceHelpInfos.getStuckedAgents()) {

						Human agent = (Human) this.worldInfo.getEntity(entityID);
						if(agent.getID()==this.agentInfo.me().getID())///////////////////////
						{
							//System.out.println("self then pass over");
							continue;
						}
						if (agent.getPosition().getValue() == cur_entity
								.getID().getValue()) {
						//	System.out.println("cur_entity.gentID");
							needClearAgent.add(entityID);
						}
					}
					//找到needClearAgent中距离自己最近的agent
					
					/**
					 * 离自己最近的被困的agent的ID
					 */
					EntityID closetAgentID = null;
					
					/**
					 * 自己与最近的被困的agent的距离
					 */
					double mindis = Double.MAX_VALUE;
					for (EntityID entityID : needClearAgent) {
						double dis = Ruler.getDistance(
								this.worldInfo.getLocation(this.agentInfo.me().getID()), 
								this.worldInfo.getLocation(entityID));
						if (dis < mindis) {
						//	System.out.println("5");
							mindis = dis;
							closetAgentID = entityID;
						}

					}
					if (closetAgentID != null) {

						Pair<Integer, Integer> curLoc = this.worldInfo.getLocation(closetAgentID);
						//距离自己最近的被埋的agent的X坐标
						int xcoord = curLoc.first();
						//距离自己最近的被埋的agent的Y坐标
						int ycoord = curLoc.second();

						//如果在自己的清理范围内
						if (mindis < repairDistance-2500) {
					//		System.out.println("7");
							
							
							Vector2D v = new Vector2D(xcoord - x, ycoord - y);
							v = v.normalised().scale(repairDistance - 500);
							
							int destX = (int) (x + v.getX()), destY = (int) (y + v.getY());
							
							//如果自己就在closetAgent所在的位置或自己被困住
							if(xcoord==x && ycoord==y)////////////////////
							{
								StandardEntity se = AgentTools.selfPosition(this.agentInfo, this.worldInfo);
								Area ro=(Area) se;
								if(ro != null) {
									for(EntityID e : ro.getBlockades())
									{
										StandardEntity en = this.worldInfo.getEntity(e);
										Blockade nearesttBlockade = null;
										double minnDistance = Double.MAX_VALUE;
										if(en instanceof Blockade)
										{
											Blockade bloc=(Blockade) en;
											double dis = AgentTools.getDistanceToBlockade(bloc, x, y);
											
											if (dis < minnDistance) {
												minnDistance = dis;
												nearesttBlockade = bloc;
											}
											if(nearesttBlockade!=null)
											{
												destX=nearesttBlockade.getX();
												destY=nearesttBlockade.getY();
												break;
											}
										}
									}
								}
							}
							this.result = new ActionClear(destX, destY);
							return ;
						}
						//如果超出了自己的清理范围，需要发送移动命令
						else {
							//System.out.println("9");/////
							if(closetAgentID==this.agentInfo.me().getID()){
								
								xcoord=x;
								ycoord=y;
							}
							
							List<EntityID> pathList = new ArrayList<>();
							pathList.add(currentArea.getID());
							
							this.result = new ActionMove(pathList, xcoord, ycoord);
							return ;
						}

					}
					//如果没有最近的被困的agent
					else {
						lastCyclePath = null;
						return;
					}
				}

				// 遍历blockade，求最近
				//System.out.println("11");
				for (EntityID next : road.getBlockades()) {
					StandardEntity en = this.worldInfo.getEntity(next);
					if (!(en instanceof Blockade))
						continue;
					Blockade bloc = (Blockade) en;
				//	System.out.println("lengthofapexes:"+bloc.getApexes().length);
					if (bloc.isApexesDefined() && bloc.getApexes().length < 6)
						continue;
					double dis = AgentTools.getDistanceToBlockade(bloc, x, y);
					if (dis < minDistance) {
						minDistance = dis;
						nearestBlockade = bloc;
					}
				}
				
				if (nearestBlockade != null) {
					scaleClear(nearestBlockade, 2);
				}
				// 不存在最近blockade
				else {
					// 入口
					if (this.staticInfoContainerModule.getEntrance().isEntrance(road)) {

						List<EntityID> neigh_bloc = new ArrayList<>();
						for (EntityID neigh : road.getNeighbours()) {
							StandardEntity entity = this.worldInfo.getEntity(neigh);
							if (!(entity instanceof Road))
								continue;
							Road neig_road = (Road) entity;
							if (!neig_road.isBlockadesDefined())
								continue;
							neigh_bloc.addAll(neig_road.getBlockades());
						}

						minDistance = Double.MAX_VALUE;
						nearestBlockade = null;
						// 遍历邻居blockade 找到最近的blockade
						for (EntityID next : neigh_bloc) {
							StandardEntity en = this.worldInfo.getEntity(next);
							if (!(en instanceof Blockade))
								continue;
							Blockade bloc = (Blockade) en;
							if (bloc.isApexesDefined()
									&& bloc.getApexes().length < 6)
								continue;
							double dis = AgentTools.getDistanceToBlockade(bloc, x, y);
							if (dis < minDistance) {
								minDistance = dis;
								nearestBlockade = bloc;
							}
						}

						if (minDistance < repairDistance * 0.5
								&& nearestBlockade != null) {
							scaleClear(nearestBlockade, 5);
						}

					}
					lastCyclePath = null;
					return;
				}
			}
		}
		
		if (indexInPath + 1 >= pathLength)
			return;
		// 下一个
		EntityID nextArea = lastCyclePath.get(indexInPath + 1);
		Area next_A =EntityTools.getArea(nextArea, this.worldInfo);
		// 上一个
		Area last_A = null;
		if (indexInPath > 0) {
			last_A = EntityTools.getArea(lastCyclePath.get(indexInPath - 1), this.worldInfo);
		}
		// 下一个边
		Edge dirEdge = null;
		for (Edge nextEdge : currentArea.getEdges()) {
			if (!nextEdge.isPassable())
				continue;

			if (nextEdge.getNeighbour().getValue() == nextArea.getValue()) {
				dirEdge = nextEdge;
				break;
			}
		}
		if (dirEdge == null)
			return;
		// 边中点
		Point2D dirPoint = new Point2D((dirEdge.getStart().getX() + dirEdge
				.getEnd().getX()) / 2.0, (dirEdge.getStart().getY() + dirEdge
				.getEnd().getY()) / 2.0);

		Set<Blockade> c_a_Blockades = getBlockades(currentArea, next_A, selfL,
				dirPoint);

		minDistance = Double.MAX_VALUE;
		nearestBlockade = null;
		for (Blockade bloc : c_a_Blockades) {
			double dis = AgentTools.getDistanceToBlockade(bloc, x, y);
			if (dis < minDistance) {
				minDistance = dis;
				nearestBlockade = bloc;
			}
		}

		if (nearestBlockade != null) {
			directionClear(nearestBlockade, dirPoint, next_A, 1);
		} else {
			Vector2D vector = selfL.minus(dirPoint);
			vector = vector.normalised().scale(repairDistance - 500);
			Line2D line = new Line2D(selfL, vector);
			Point2D r_dirPoint = line.getEndPoint();
			Set<Blockade> c_a_r_Blockades = getBlockades(currentArea, last_A,
					selfL, r_dirPoint);

			minDistance = Double.MAX_VALUE;
			nearestBlockade = null;
			for (Blockade bloc : c_a_r_Blockades) {
				double dis = AgentTools.getDistanceToBlockade(bloc, x, y);
				if (dis < minDistance) {
					minDistance = dis;
					nearestBlockade = bloc;
				}
			}

			if (nearestBlockade != null) {
				reverseClear(nearestBlockade, r_dirPoint, last_A, 1);
			}
		}

		for (int i = indexInPath + 1; i <= indexInPath + 5; i++) {
			if (pathLength > i + 1) {
				// Point2D startPoint = dirPoint;
				StandardEntity entity_1 = this.worldInfo.getEntity(lastCyclePath.get(i));
				StandardEntity entity_2 = this.worldInfo.getEntity(lastCyclePath.get(i + 1));

				if (entity_1 instanceof Area && entity_2 instanceof Area) {
					Area next_a_1 = (Area) entity_1;
					Area next_a_2 = (Area) entity_2;

					for (Edge edge : next_a_1.getEdges()) {
						if (!edge.isPassable())
							continue;
						if (edge.getNeighbour().getValue() == next_a_2.getID()
								.getValue()) {
							dirPoint = new Point2D(
									(edge.getStartX() + edge.getEndX()) / 2.0,
									(edge.getStartY() + edge.getEndY()) / 2.0);
							break;
						}
					}

					Set<Blockade> n_a_blockades = getBlockades(next_a_1,
							next_a_2, selfL, dirPoint);

					String str = null;
					for (Blockade n_b : n_a_blockades) {
						if (str == null) {
							str = n_b.getID().getValue() + "";
						} else {
							str = str + ", " + n_b.getID().getValue();
						}
					}

					minDistance = Double.MAX_VALUE;
					nearestBlockade = null;
					for (Blockade bloc : n_a_blockades) {
						double dis = AgentTools.getDistanceToBlockade(bloc, x, y);
						if (dis < minDistance) {
							minDistance = dis;
							nearestBlockade = bloc;
						}
					}

					if (nearestBlockade != null) {
						directionClear(nearestBlockade, dirPoint, next_a_2, 2);
					}
				}
			} else if (pathLength == i + 1) {

				// change to the follow 201409

				// ///////////////////////
				EntityID endEntityID = lastCyclePath.get(i);
				StandardEntity endEntity = this.worldInfo.getEntity(endEntityID);
				if (!(endEntity instanceof Road)) {
					continue;
				}
				Road endRoad = EntityTools.getRoad(lastCyclePath.get(i), this.worldInfo);
				
				if (!this.staticInfoContainerModule.getEntrance().isEntrance(endRoad)
						&& !this.criticalArea.isCriticalArea(endEntityID)) {

					// changeset 中的at pf，
					List<EntityID> inChangeSetAT_FB = new ArrayList<>();
					// blockade
					List<EntityID> inChangeSetBlockades = new ArrayList<>();

					for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
						StandardEntity entity = this.worldInfo.getEntity(next);
						if (entity instanceof AmbulanceTeam
								|| entity instanceof FireBrigade) {
							inChangeSetAT_FB.add(next);
						} else if (entity instanceof Blockade) {
							inChangeSetBlockades.add(next);
						}
					}
					HashSet<EntityID> needClearAgent = new HashSet<>();
					for (EntityID agent_id : inChangeSetAT_FB) {
					//	System.out.println("at-fb");///////////
						Human agent = (Human) this.worldInfo.getEntity(agent_id);
						for (EntityID blockade_id : inChangeSetBlockades) {
							Blockade blockade = (Blockade) this.worldInfo.getEntity(blockade_id);
							double dis = Ruler.getDistanceToBlock(blockade,
									new Point(agent.getX(), agent.getY()));
							if (dis < 2000) {
								needClearAgent.add(agent_id);
							}
						}

					}
					for (EntityID entityID : this.policeForceHelpInfos.getStuckedAgents()) {
					//	System.out.println("stuckedagents");///////////
						Human agent = (Human) this.worldInfo.getEntity(entityID);
						if ((this.worldInfo.getEntity(agent.getPosition())) instanceof Building) {
							continue;
						}
							needClearAgent.add(entityID);
							
					}
					needClearAgent.addAll(this.policeForceHelpInfos.getStuckedAgents());
				//	System.out.println("getstuckedagents:"+world.getStuckedAgents());///////////
					EntityID closetAgentID = null;
					double mindis = Double.MAX_VALUE;
					for (EntityID entityID : needClearAgent) {
						StandardEntity entity = this.worldInfo.getEntity(entityID);
						double dis = Ruler.getDistance(this.x, this.y, 
								this.worldInfo.getLocation(entity).first(),
								this.worldInfo.getLocation(entity).second());
						if (dis < mindis) {
							mindis = dis;
							closetAgentID = entityID;
						}

					}
					if (closetAgentID != null) {

						StandardEntity closetAgent = this.worldInfo.getEntity(closetAgentID);
						int xcoord = this.worldInfo.getLocation(closetAgent).first();
						int ycoord = this.worldInfo.getLocation(closetAgent).second();

						if (mindis < repairDistance - 1500) {

							Vector2D v = new Vector2D(xcoord - x, ycoord - y);
							v = v.normalised().scale(repairDistance - 500);
							int destX = (int) (x + v.getX()), destY = (int) (y + v
									.getY());							
							
							this.result = new ActionClear(destX, destY);

							return ;
						}

					}
				} else {
					System.out.println("stuckedagent=null");///////////
					minDistance = Double.MAX_VALUE;
					nearestBlockade = null;
					StandardEntity entity = this.worldInfo.getEntity(lastCyclePath.get(i));
					if (!(entity instanceof Road))
						continue;
					Road destRoad = (Road) entity;
					if (!destRoad.isBlockadesDefined())
						continue;
					for (EntityID next : destRoad.getBlockades()) {
						StandardEntity en = this.worldInfo.getEntity(next);
						if (!(en instanceof Blockade))
							continue;
						Blockade bloc = (Blockade) this.worldInfo.getEntity(next);
						if (bloc.isApexesDefined()
								&& bloc.getApexes().length < 6)
							continue;
						double dis = AgentTools.getDistanceToBlockade(bloc, x, y);
						if (dis < minDistance) {
							minDistance = dis;
							nearestBlockade = bloc;
						}
					}

					if (nearestBlockade != null) {
						scaleClear(nearestBlockade, 3);
					} else {
						break;
					}

				}
				// ///////////////////////

			}
		}
	}

	/**
	 * @param target
	 * @param marker
	 * @throws ActionCommandException
	 *             老方法清除路障target
	 */
	private void scaleClear(Blockade target, int marker) {
		double distance = AgentTools.getDistanceToBlockade(target, x, y);
		// 能看见并且在可清除范围内
		if (distance < repairDistance && AgentTools.canSee(target, this.worldInfo)) {
			
			this.result = new ActionClear(target);
			return ;
		} else {
			int current_I = findIndexInPath(lastCyclePath, EntityTools.getSelfPosition(this.agentInfo, this.worldInfo).getID());

			List<EntityID> path = getPathToBlockade(current_I, target);

			if (path.size() > 0) {

				this.result = new ActionMove(path, target.getX(), target.getY());
				lastClearDest_x = -1;
				lastClearDest_y = -1;
				return ;
			}
		}
	}

	private void directionClear(Blockade target, Point2D dirPoint, Area next_A, int marker) {

		
		if (!AgentTools.canSee(target.getID(), this.worldInfo)) {
			int current_I = findIndexInPath(lastCyclePath, 
					EntityTools.getSelfPosition(this.agentInfo, this.worldInfo).getID());

			List<EntityID> path = getPathToBlockade(current_I, target);

			Point2D movePoint = getMovePoint(target, dirPoint);
			if (movePoint != null) {
				this.result = new ActionMove(path, 
						(int) movePoint.getX(), (int) movePoint.getY());
			} else {
				this.result = new ActionMove(path, target.getX(), target.getY());
			}
			lastClearDest_x = -1;
			lastClearDest_y = -1;

			return ;
		}

		double dis_to_dir = Math
				.hypot(dirPoint.getX() - x, dirPoint.getY() - y);
		Vector2D v = new Vector2D(dirPoint.getX() - x, dirPoint.getY() - y);
		v = v.normalised().scale(Math.min(dis_to_dir, repairDistance - 500));
		Point2D t_dir_p = new Point2D(x + v.getX(), y + v.getY());

		Road road = (Road) this.worldInfo.getEntity(target.getPosition());

		Set<Blockade> t_bloc = getBlockades(road, next_A, new Point2D(x, y),
				t_dir_p);
		if (t_bloc.size() > 0) {
			if (dis_to_dir < repairDistance) {
				v = v.normalised().scale(repairDistance);
			} else {
				v = v.normalised().scale(dis_to_dir);
			}

			int destX = (int) (x + v.getX()), destY = (int) (y + v.getY());
			timeLock(destX, destY, target);

			this.result = new ActionClear(destX, destY);
			return ;
		} else {
			int current_I = findIndexInPath(lastCyclePath, 
					EntityTools.getSelfPosition(this.agentInfo, this.worldInfo).getID());

			List<EntityID> path = getPathToBlockade(current_I, target);

			String str = null;
			for (EntityID pa : path) {
				if (str == null)
					str = pa.getValue() + "";
				else
					str = str + "," + pa.getValue();
			}

			Point2D movePoint = getMovePoint(target, dirPoint);
			if (movePoint != null) {
				this.result = new ActionMove(path, 
						(int) movePoint.getX(), (int) movePoint.getY());
			} else {
				this.result = new ActionMove(path, 
						(int) dirPoint.getX(), (int) dirPoint.getY());
			}
			lastClearDest_x = -1;
			lastClearDest_y = -1;
			return ;
		}
	}

	private void reverseClear(Blockade target, Point2D dirPoint, Area last_A, int marker){
		if (!AgentTools.canSee(target.getID(), this.worldInfo))
			return;

		double dis_to_dir = Math
				.hypot(dirPoint.getX() - x, dirPoint.getY() - y);
		Vector2D v = new Vector2D(dirPoint.getX() - x, dirPoint.getY() - y);
		v = v.normalised().scale(Math.min(dis_to_dir, repairDistance - 500));
		Point2D t_dir_p = new Point2D(x + v.getX(), y + v.getY());

		Road road = (Road) this.worldInfo.getEntity(target.getPosition());

		Set<Blockade> t_bloc = getBlockades(road, last_A, new Point2D(x, y),
				t_dir_p);
		if (t_bloc.size() > 0) {
			if (dis_to_dir < repairDistance) {
				v = v.normalised().scale(repairDistance);
			}

			int destX = (int) (x + v.getX()), destY = (int) (y + v.getY());

			timeLock(destX, destY, target);
			if (reverseTimeLock(destX, destY, target))
				return;
			
			this.result = new ActionClear(destX, destY);
			return ;
		}
	}

	/**
	 * 获取从lastCyclePath[current_L_I]到blockade的路径
	 * 
	 * 如果blockade所在的area在lastCyclePath中，则返回lastCyclePath中的路径
	 * 如果不在的话，返回的路径仅包含1个点，lastCyclePath[current_L_I]
	 * 
	 * @param current_L_I
	 * @param blockade
	 * @return 重新获取路径
	 */
	private List<EntityID> getPathToBlockade(int current_L_I, Blockade blockade) {
		List<EntityID> path = new ArrayList<>();
		if (!blockade.isPositionDefined()) {
			path.add(EntityTools.getSelfPosition(this.agentInfo, this.worldInfo).getID());
			return path;
		}

		EntityID blo_A = blockade.getPosition();
		int b_index = findIndexInPath(lastCyclePath, blo_A);

		if (b_index < lastCyclePath.size()) {
			for (int i = current_L_I; i <= b_index; i++)
				path.add(lastCyclePath.get(i));
		} 
		if (path.isEmpty()) {
			path.add(EntityTools.getSelfPosition(this.agentInfo, this.worldInfo).getID());
		}
		return path;
	}

	public void doClear(Road road, Edge dir, Blockade target) {
		this.result = new ActionClear(target);
		return ;
	}

	/**
	 * 计算location在path中的索引
	 * @param path
	 * @param location
	 * @return
	 */
	private int findIndexInPath(List<EntityID> path, EntityID location) {
		int index = 0;
		for (EntityID next : path) {
			if (location.getValue() == next.getValue())
				break;
			index++;
		}
		return index;
	}

	private void timeLock(int destX, int destY, Blockade target){
		if (lastClearDest_x == destX && lastClearDest_y == destY) {
			if (count >= lock) {
				int current_I = findIndexInPath(lastCyclePath, 
						this.worldInfo.getPosition(this.agentInfo.me().getID()).getID());
				List<EntityID> path = getPathToBlockade(current_I, target);


				//移动的话，没有清理路障，所以将lastClearDest_x/y设置为-1
				this.result = new ActionMove(path, destX, destY);
				lastClearDest_x = -1;
				lastClearDest_y = -1;
				count = 0;
				return ;
			} else {
				count++;
			}
		} else {
			count = 0;
			lastClearDest_x = destX;
			lastClearDest_y = destY;
		}
	}

	private boolean reverseTimeLock(int destX, int destY, Blockade target) {
		if (lastClearDest_x == destX && lastClearDest_y == destY) {
			if (count >= reverseLock) {
				destX = -1;
				destY = -1;
				return true;
			} else {
				count++;
				return false;
			}
		} else {
			count = 0;
			lastClearDest_x = destX;
			lastClearDest_y = destY;
			return false;
		}
	}

	/**
	 * 计算阻碍移动的所有blockades
	 * 具体方法为：将线段selfL - dirPoint扩张成一个管道，
	 * 如果current_A或者next_A上的blockade覆盖了管道或者覆盖了起点，
	 * 就将这个blockade加入要返回的集合
	 * @param current_A
	 * @param next_A
	 * @param selfL
	 * @param dirPoint
	 * @return
	 */
	private Set<Blockade> getBlockades(Area current_A, Area next_A,
			Point2D selfL, Point2D dirPoint) {
		if (current_A instanceof Building && next_A instanceof Building)
			return new HashSet<Blockade>();
		Set<EntityID> allBlockades = new HashSet<>();

		Road currentRoad = null, nextRoad = null;
		if (current_A instanceof Road) {
			currentRoad = (Road) current_A;
		}
		if (next_A instanceof Road) {
			nextRoad = (Road) next_A;
		}

		rescuecore2.misc.geometry.Line2D line = new rescuecore2.misc.geometry.Line2D(
				selfL, dirPoint);
		rescuecore2.misc.geometry.Line2D[] temp = getParallelLine(line, 500);

		Polygon po_1 = new Polygon();
		po_1.addPoint((int) temp[0].getOrigin().getX(), (int) temp[0]
				.getOrigin().getY());
		po_1.addPoint((int) temp[0].getEndPoint().getX(), (int) temp[0]
				.getEndPoint().getY());
		po_1.addPoint((int) temp[1].getEndPoint().getX(), (int) temp[1]
				.getEndPoint().getY());
		po_1.addPoint((int) temp[1].getOrigin().getX(), (int) temp[1]
				.getOrigin().getY());
		java.awt.geom.Area area = new java.awt.geom.Area(po_1);

		Set<Blockade> results = new HashSet<Blockade>();

		if (currentRoad != null && currentRoad.isBlockadesDefined()) {
			allBlockades.addAll(currentRoad.getBlockades());
		}
		if (nextRoad != null && nextRoad.isBlockadesDefined()) {
			allBlockades.addAll(nextRoad.getBlockades());
		}

		for (EntityID blockade : allBlockades) {
			StandardEntity entity = this.worldInfo.getEntity(blockade);
			if (entity == null)
				continue;
			if (!(entity instanceof Blockade))
				continue;
			Blockade blo = (Blockade) entity;

			if (!blo.isApexesDefined())
				continue;
			if (blo.getApexes().length < 6)
				continue;
			Polygon po = Util.getPolygon(blo.getApexes());
			java.awt.geom.Area b_Area = new java.awt.geom.Area(po);
			b_Area.intersect(area);
			if (!b_Area.getPathIterator(null).isDone()
					|| blo.getShape().contains(selfL.getX(), selfL.getY()))
				results.add(blo);
		}

		return results;
	}

	/**
	 * Get the parallel lines(both left and right sides) of the given line. The
	 * distance is specified by rad.
	 * 
	 * @param line
	 *            the given line
	 * @param rad
	 *            the distance
	 * @return the two parallel lines of the given line
	 */
	private Line2D[] getParallelLine(Line2D line, int rad) {
		float theta = (float) Math.atan2(line.getEndPoint().getY()
				- line.getOrigin().getY(), line.getEndPoint().getX()
				- line.getOrigin().getX());
		theta = theta - (float) Math.PI / 2;
		while (theta > Math.PI || theta < -Math.PI) {
			if (theta > Math.PI)
				theta -= 2 * Math.PI;
			else
				theta += 2 * Math.PI;
		}
		int t_x = (int) (rad * Math.cos(theta)), t_y = (int) (rad * Math
				.sin(theta));

		Point2D line_1_s, line_1_e, line_2_s, line_2_e;
		line_1_s = new Point2D(line.getOrigin().getX() + t_x, line.getOrigin()
				.getY() + t_y);
		line_1_e = new Point2D(line.getEndPoint().getX() + t_x, line
				.getEndPoint().getY() + t_y);

		line_2_s = new Point2D(line.getOrigin().getX() - t_x, line.getOrigin()
				.getY() - t_y);
		line_2_e = new Point2D(line.getEndPoint().getX() - t_x, line
				.getEndPoint().getY() - t_y);

		Line2D[] result = { new Line2D(line_1_s, line_1_e),
				new Line2D(line_2_s, line_2_e) };

		return result;
	}

	/**
	 * 计算blockade上距离dirPoint最远的点
	 * 实际返回的值还会将这个点向外延伸一小段距离
	 * @param target
	 * @param dirPoint
	 * @return
	 */
	private Point2D getMovePoint(Blockade target, Point2D dirPoint) {
		if (target == null || dirPoint == null)
			return null;
		if (!target.isPositionDefined())
			return null;
		EntityID b_location = target.getPosition();

		StandardEntity entity = this.worldInfo.getEntity(b_location);
		if (!(entity instanceof Area))
			return null;
		Area b_area = (Area) entity;

		/**
		 * 目标区域的中点
		 */
		Point2D center_p = new Point2D(b_area.getX(), b_area.getY());

		Vector2D vector = center_p.minus(dirPoint);
		//正规化，再放大100000倍
		vector = vector.normalised().scale(100000);

		center_p = dirPoint.plus(vector);
		// dirPoint = dirPoint.plus(vector);

		Line2D line = new Line2D(dirPoint, center_p);
		// 相交的点
		Set<Point2D> intersections = Util.getIntersections(
				Util.getPolygon(target.getApexes()), line);

		Point2D farestPoint = null;
		double maxDistance = Double.MIN_VALUE;
		for (Point2D next : intersections) {
			double dis = Ruler.getDistance(dirPoint, next);
			if (dis > maxDistance) {
				maxDistance = dis;
				farestPoint = next;
			}
		}

		if (farestPoint != null) {
			Line2D line_2 = new Line2D(dirPoint, farestPoint);
			line_2 = Util.improveLine(line_2, 500);

			return line_2.getEndPoint();
		}

		return null;
	}
	
}