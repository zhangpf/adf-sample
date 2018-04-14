package NUDT.module.algorithm.pathplanning.pov;

import java.awt.Point;
import java.util.ArrayList;
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
import NUDT.utils.extendTools.EntityTools;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class POVRouter extends PathPlanning {

    private EntityID from;
    private Collection<EntityID> targets;
    private List<EntityID> result;
	
	private final PassableDictionary passableDic;
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
		
		this.pov = new PointOfVisivility(this.worldInfo);
		this.passableDic = new PassableDictionary(this.agentInfo, this.scenarioInfo, this.worldInfo);
		this.uftReachable = new UFTReachableArea(this.worldInfo);
	
		final int routeThinkTime = (int)this.scenarioInfo.getKernelAgentsThinkTime() * 2 / 3;

		this.search = new POVSearch(routeThinkTime, pov);
		
		this.normalFunc = CostFunctionFactory.normal(passableDic, wi);
		this.strictFunc = CostFunctionFactory.strict(passableDic, wi);
		this.searchFunc = CostFunctionFactory.search(passableDic, wi);
		
		this.pfFunc = CostFunctionFactory.pf();
	}
	
	public void update(final EntityID pos, final Set<EntityID> visibleEntitiesID) {
		Set<EdgeNode> newPassables = passableDic.update(pos, this, visibleEntitiesID);

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

	
	/**
	 * 根据agent类型与this.targets确定要使用的CostFunction
	 * @return
	 */
	private CostFunction getCostFunc()
	{
		CostFunction cf;
		StandardEntityURN urn = this.agentInfo.me().getStandardURN();
		switch (urn) {
		case AMBULANCE_TEAM:
			cf = this.normalFunc;
			break;
		case FIRE_BRIGADE:
			if(this.targets.size() > 1)
				cf = this.normalFunc;
			else if (this.targets.size() == 1){
				Building building = EntityTools.getBuilding(this.targets.iterator().next(), this.worldInfo);
				if(building != null)
				{
					cf = getFbCostFunction(building);
					break;
				}
			}
			cf = this.normalFunc;
			break;
		case POLICE_FORCE:
			cf = this.pfFunc;
			break;
		default:
			cf = this.normalFunc;
			break;
		}
		return cf;
	}
	
	
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
		
		this.result = null;
		
		StandardEntity from_entity = this.worldInfo.getEntity(this.from);
		Pair<Integer, Integer> mypos = this.worldInfo.getLocation(this.agentInfo.me());;
		if(this.targets.size() < 1)
		{ }
		else if(this.targets.size() == 1)
		{
			StandardEntity target_entity = this.worldInfo.getEntity(this.targets.iterator().next());
			
			if(target_entity instanceof Area)
			{
				if(from_entity instanceof Area)
				{
					this.result = getAStar((Area)from_entity, (Area)target_entity, 
						this.getCostFunc(), new Point(mypos.first().intValue(), mypos.second().intValue()));
				}
				else if(from_entity instanceof Human)
				{
					this.result = getAStar((Human)from_entity, (Area)target_entity, this.getCostFunc());
				}
			}
		}
		else 
		{
			List<StandardEntity> targetentities = new ArrayList<StandardEntity>();
			for(EntityID entityID : this.targets)
			{
				StandardEntity tmp = this.worldInfo.getEntity(entityID);
				if(tmp != null)
					targetentities.add(tmp);
			}
			if(from_entity instanceof Area)
			{
				this.result = getMultiAStar((Area)from_entity, targetentities, 
					this.getCostFunc(), new Point(mypos.first().intValue(), mypos.second().intValue()));
			}
			else if(from_entity instanceof Human)
			{
				StandardEntity positionOfFromEntity = this.worldInfo.getPosition(this.from);
				if(positionOfFromEntity != null && positionOfFromEntity instanceof Area) 
				{
					this.result = this.getMultiAStar((Area)positionOfFromEntity, targetentities, 
							this.getCostFunc(), new Point(mypos.first().intValue(), mypos.second().intValue()));
				}
			}
		}
		return this;
	}
}
