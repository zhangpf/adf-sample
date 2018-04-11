package NUDT.module.algorithm.pathplanning.pov;

import java.awt.Point;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import NUDT.module.algorithm.pathplanning.pov.graph.AreaNode;
import NUDT.module.algorithm.pathplanning.pov.graph.EdgeNode;
import NUDT.module.algorithm.pathplanning.pov.graph.PassableDictionary;
import NUDT.module.algorithm.pathplanning.pov.graph.PointOfVisivility;
import NUDT.module.algorithm.pathplanning.pov.graph.PassableDictionary.PassableLevel;
import NUDT.module.algorithm.pathplanning.pov.reachable.UFTReachableArea;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

public class POVRouter extends PathPlanning {
	
	private Map<EntityID, Set<EntityID>> graph;

    private EntityID from;
    private Collection<EntityID> targets;
    private List<EntityID> result;
	
	
	private final PassableDictionary passableDic;
//	private BFSReachableArea bfsReachable;
	private final UFTReachableArea uftReachable;
	private final PointOfVisivility pov;
	private final POVSearch search;
	
	private double routeCost;
	
	public POVSearch search() {
		return search;
	}

	private final CostFunction normalFunc;
	private final CostFunction strictFunc;
	private final CostFunction searchFunc;
	private final CostFunction pfFunc;

	public PassableDictionary getPassableDic() {
		return passableDic;
	}

	public POVRouter(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		// public POVRouter(Human me, AdvancedWorldModel world) {
		
		pov = new PointOfVisivility(this.worldInfo.getRawWorld());
		passableDic = new PassableDictionary(this.agentInfo, this.scenarioInfo, this.worldInfo);
//		bfsReachable = new BFSReachableArea(world);
		uftReachable = new UFTReachableArea(this.worldInfo);
	
		final int routeThinkTime = (int)this.scenarioInfo.getKernelAgentsThinkTime() * 2 / 3;
		;
		search = new POVSearch(routeThinkTime, pov);
		
		normalFunc = CostFunctionFactory.normal(passableDic, wi);
		strictFunc = CostFunctionFactory.strict(passableDic, wi);
		searchFunc = CostFunctionFactory.search(passableDic, wi);
		
		pfFunc = CostFunctionFactory.pf();
	}
	
	public void update(final EntityID pos, final Set<EntityID> visibleEntitiesID) {
		Set<EdgeNode> newPassables = passableDic.update(pos, this, visibleEntitiesID);
//		if (bfsReachable != null) {
//			try {
//				bfsReachable.update(pov.get(pos), world);
//			} catch (IllegalStateException e) {
//				bfsReachable = null;
//			}
//		}
		if (uftReachable != null) {
			uftReachable.update(this.agentInfo, this.scenarioInfo, this, newPassables, this.worldInfo);
		}
	}

	public List<EntityID> getAStar(Human me, Area destination, CostFunction costFunc) {
		POVPath result = search.getAStarPath(me, destination, costFunc);
		if (result == null) {
			routeCost = 0;
			return Collections.singletonList(me.getPosition());
		}
		
		routeCost = result.cost();
		return result.getRoute();
	}
	public List<EntityID> getAStar(Area from, Area destination, CostFunction costFunc, Point start) {
		POVPath result = search.getAStarPath(from, destination, costFunc, start);
		if (result == null) {
			routeCost = 0;
			return Collections.singletonList(from.getID());
		}
		
		routeCost = result.cost();
		return result.getRoute();
	}
	
	public List<EntityID> getAStar(Area from, Area destination, Point start) {
		POVPath result = search.getAStarPath(from, destination, getNormalCostFunction(), start);
		if (result == null) {
			routeCost = 0;
			return Collections.singletonList(from.getID());
		}
		
		routeCost = result.cost();
		return result.getRoute();
	}
	
	public List<EntityID> getMultiDest(Area origin, 
			Collection<? extends StandardEntity> destinations, Point start) {
		return getMultiAStar(origin, destinations, this.getNormalCostFunction(), start);
	}
	
	public List<EntityID> getMultiDest(Area origin, 
			Collection<? extends StandardEntity> destinations, CostFunction costFunc, Point start) {
		return getMultiAStar(origin, destinations, costFunc, start);
		//return getDijkstra(origin, new HashSet<StandardEntity>(destinations), costFunc);
	}

	// unused path searcher
	public List<EntityID> getDijkstra(Area origin, Set<StandardEntity> destinations, CostFunction costFunc, Point start) {
		POVPath result = search.getDijkstraPath(origin, destinations, costFunc, start);
		if (result == null) {
			return Collections.singletonList(origin.getID());
		}
		return result.getRoute();
	}

	public List<EntityID> getMultiAStar(Area origin, 
			Collection<? extends StandardEntity> destinations, CostFunction costFunc, Point start) {
		POVPath result = search.getMultiAStarPath(origin, destinations, costFunc, start);
		if (result == null) {
			routeCost = 0;
			return Collections.singletonList(origin.getID());
		}
		
		routeCost = result.cost();
		return result.getReverseRoute();
	}

	public CostFunction getNormalCostFunction() {
		return normalFunc;
	}
	
	public CostFunction getStrictCostFunction() {
		return strictFunc;
	}
	
	public CostFunction getSearchCostFunction() {
		return searchFunc;
	}
	
	public CostFunction getPfCostFunction() {
		return pfFunc;
	}
	
	public CostFunction getFbCostFunction(Building dest) {
		return CostFunctionFactory.fb(this.scenarioInfo, passableDic, dest, this.worldInfo);
	}
	
	public CostFunction getAtCostFunction(final Map<EntityID, Double> minStaticCost) {
		return CostFunctionFactory.at(passableDic, minStaticCost, this.worldInfo);
	}

	public PointOfVisivility getPOV() {
		return pov;
	}
	
	/**
	 * Determines whether a area is sure reachable.
	 * <p>
	 * Areas with {@link PassableLevel#SURE_PASSABLE SURE_PASSABLE},
	 * {@link PassableLevel#COMMUNICATION_PASSABLE COMMUNICATION_PASSABLE} and
	 * {@link PassableLevel#LOGICAL_PASSABLE LOGICAL_PASSABLE} are sure passable
	 * areas.
	 * 
	 * @param Area
	 *            the target area
	 * @return true if the target area is sure reachable. Otherwise, false.
	 */
	public boolean isSureReachable(Area area) {
		return isSureReachable(area.getID());
	}
	
	/**
	 * Determines whether a area is sure reachable.
	 * <p>
	 * Areas with {@link PassableLevel#SURE_PASSABLE SURE_PASSABLE},
	 * {@link PassableLevel#COMMUNICATION_PASSABLE COMMUNICATION_PASSABLE} and
	 * {@link PassableLevel#LOGICAL_PASSABLE LOGICAL_PASSABLE} are sure passable
	 * areas.
	 * 
	 * @param id
	 *            the target area
	 * @return true if the target area is sure reachable. Otherwise, false.
	 */
	public boolean isSureReachable(EntityID id) {
//		if (bfsReachable != null) {
//			return bfsReachable.isSureReachable(id);
//		}
		if (uftReachable != null) {
			try {
				EntityID position = this.worldInfo.getPosition(this.agentInfo.me().getID()).getID();///
				boolean flag = uftReachable.isSureReachable(position, id);
				return flag;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}
	
//	/**
//	 * Determines whether a area is passable.
//	 * <p>
//	 * Areas with {@link PassableLevel#SURE_PASSABLE SURE_PASSABLE},
//	 * {@link PassableLevel#COMMUNICATION_PASSABLE COMMUNICATION_PASSABLE},
//	 * {@link PassableLevel#LOGICAL_PASSABLE LOGICAL_PASSABLE} and
//	 * {@link PassableLevel#UNKNOWN UNKNOWN} are passable areas.
//	 * 
//	 * @param area
//	 *            the target area
//	 * @return true if the target area is sure reachable. Otherwise, false.
//	 */
//	public boolean isReachable(Area area) {
//		return isReachable(area.getID());
//	}
//	
//	/**
//	 * Determines whether a area is passable.
//	 * <p>
//	 * Areas with {@link PassableLevel#SURE_PASSABLE SURE_PASSABLE},
//	 * {@link PassableLevel#COMMUNICATION_PASSABLE COMMUNICATION_PASSABLE},
//	 * {@link PassableLevel#LOGICAL_PASSABLE LOGICAL_PASSABLE} and
//	 * {@link PassableLevel#UNKNOWN UNKNOWN} are passable areas.
//	 * 
//	 * @param id
//	 *            the target area
//	 * @return true if the target area is sure reachable. Otherwise, false.
//	 */
//	public boolean isReachable(EntityID id) {
//		if (bfsReachable != null) {
//			return bfsReachable.isReachable(id);
//		}
//		return false;
//	}
	
	/**
	 * Get the number of edges with given <code>PassableLevel</code> of the
	 * target area.
	 * <p>
	 * Currently, this method is useless.
	 * 
	 * @param areaID
	 *            the target area
	 * @param level
	 *            the given passable level
	 * @return the number of edges with the given passable level
	 */
	public int getPassableLevelCount(EntityID areaID, PassableLevel level) {
		final AreaNode areaNode = pov.get(areaID);
		if (areaNode == null) 
			return 0;
		int count = 0;
		for (EdgeNode p : areaNode.getNeighbours()) {
			if (getPassableDic().getPassableLevel(areaNode, p, null, this.worldInfo) == level) {
				count++;
			}
		}
		return count;
	}
	
	/**
	 * Currently, this method is useless.
	 */
	public POVPath amend(Human me, POVPath old) {
		AreaNode currentNode = pov.get(me.getPosition());
		for (POVPath current = old; current.getPrevious() != null; current = current.getPrevious()) {
			if (current.getPoint().equals(currentNode)) {
				System.out.println("amend old route");
				current.setPrevious(null);
				return old;
			}
    	}
	
		System.out.println("Out of old route");
		return search.getAStarPath(me, 
				(Area) this.worldInfo.getEntity(old.getRoute().get(0)), getNormalCostFunction()).add(old);
	}
	
	/**
	 * Get the real cost of the newly selected path.
	 * 
	 * @return the real cost of newly selected path
	 */
	public double getRouteCost() {
		return routeCost;
	}

	
	
	
	
	//derived from PathPlanning
	
	
	@Override
	public List<EntityID> getResult() {
		return this.result;
	}

	@Override
	public PathPlanning setFrom(EntityID id) {
		this.from = id;
		return this;
	}

	@Override
	public PathPlanning setDestination(Collection<EntityID> targets) {
		this.targets = targets;
        return this;
	}

	@Override
	public PathPlanning calc() {
		// TODO Auto-generated method stub
		return null;
	}
}
