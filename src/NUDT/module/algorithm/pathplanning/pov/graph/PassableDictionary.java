package NUDT.module.algorithm.pathplanning.pov.graph;

import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import NUDT.utils.AgentConstants;
import NUDT.module.algorithm.pathplanning.pov.POVPath;
import NUDT.module.algorithm.pathplanning.pov.POVRouter;
import NUDT.utils.SetPair;
import NUDT.module.complex.utils.RoadTools;
import NUDT.module.complex.utils.WorldTools;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

public class PassableDictionary {
	/**
	 * The world model.
	 */
	final private WorldInfo wi;
	
	final private AgentInfo ai;
	
	final private ScenarioInfo si;
	
	/**
	 * A map stores tuple and its passable level pairs. A tuple is a
	 * {@link AreaNode} and one of its neighboured {@link EdgeNode}.
	 */
	final private HashMap<SetPair<PointNode, PointNode>, PassableLevel> passableMap;
	
	/**
	 * The position of this Agent in last cycle. If you get a path between the
	 * last position and the current position, then all nodes in this path is
	 * {@link PassableLevel#LOGICAL_PASSABLE}.
	 */
	private EntityID preArea = null;
	
	/**
	 * The position of other Agents in last cycle. If you get a path between the
	 * last position and the current position, the all nodes in this path is
	 * {@link PassableLevel#LOGICAL_PASSABLE}.
	 */
	private List<Pair<EntityID, EntityID>> preAgentArea = new ArrayList<Pair<EntityID, EntityID>>();
	
	public enum PassableLevel {
		SURE_PASSABLE,
		PARTLT_PASSABLE,
		COMMUNICATION_PASSABLE,
		LOGICAL_PASSABLE,
		UNPASSABLE,
		UNKNOWN; 
		
		public boolean isPassable() {
			return this.ordinal() < UNPASSABLE.ordinal();
		}
	}
	
	@SuppressWarnings("serial")
	public PassableDictionary(AgentInfo ai, ScenarioInfo si, WorldInfo wi) {
		this.ai = ai;
		this.si = si;
		this.wi = wi;
		int areaSize = WorldTools.getEntitiesOfType(AgentConstants.AREAS, wi.getRawWorld()).size();
		
		passableMap = new HashMap<SetPair<PointNode, PointNode>, PassableLevel>(areaSize * 2) {
			@Override
			public PassableLevel get(final Object o) {
				final PassableLevel value = super.get(o);
				return (value == null) ? PassableLevel.UNKNOWN : value;
			}
			
			@Override
			public PassableLevel put(final SetPair<PointNode, PointNode> key, final PassableLevel value) {
				final PassableLevel preValue = super.put(key, value);
				return (preValue == null) ? PassableLevel.UNKNOWN : preValue;
			}
		};
	}
	
	/**
	 * Update passable dictionary.
	 * 
	 * @param pos
	 *            the position of a agent
	 * @param router
	 *            the router
	 * @param visibleEntitiesID
	 *            all entities this agent can see(only considered ChangeSet)
	 * @return
	 */
	public Set<EdgeNode> update(final EntityID pos, final POVRouter router,
			final Set<EntityID> visibleEntitiesID) {

		final Set<EdgeNode> result = new HashSet<EdgeNode>();
		final PointOfVisivility pov = router.getPOV();

		result.addAll(lineCrossJudge(visibleEntitiesID, pov));

		if (ai.getTime() > si.getKernelAgentsIgnoreuntil()) {
			result.addAll(preOwnRoutePath((Area) wi.getEntity(pos), router));
			result.addAll(preAgentsRoutePath(router));
		}
		return Collections.unmodifiableSet(result);
	}
	
	/**
	 * 
	 * 
	 * @param visibleEntitiesID
	 *            all entities this agent can see(only considered ChangeSet)
	 * @param pov
	 *            all PointNode of current map
	 * @return
	 */
	private Set<EdgeNode> lineCrossJudge(Set<EntityID> visibleEntitiesID, PointOfVisivility pov) {
		Set<EdgeNode> result = new HashSet<EdgeNode>();
		for (EntityID id : visibleEntitiesID) {
			if (wi.getEntity(id) instanceof Road) {
				AreaNode areaNode = pov.get(id);
				for (EdgeNode edgeNode : areaNode.getNeighbours()) {
					// if (getPassableLevel(areaNode, edgeNode) == PassableLevel.SURE_PASSABLE)
					// 		continue;
					PassableLevel level =  isPassableLine(areaNode, edgeNode, null, wi);
					if (level != PassableLevel.UNKNOWN) {
						PassableLevel preValue = 
								passableMap.put(new SetPair<PointNode, PointNode>(areaNode, edgeNode), level);
						if ((!preValue.isPassable()) && level.isPassable()) {
							result.add(edgeNode);
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Determines the passable level of a road.
	 * 
	 * @param areaNode
	 *            the AreaNode object of this road
	 * @param edgeNode
	 *            the passable edge of this road
	 * @return the passable level of this road
	 */
	private PassableLevel isPassableLine(final AreaNode areaNode, final EdgeNode edgeNode, Point start, WorldInfo wi) {
		Area area = areaNode.getBelong();
		if (area instanceof Road) {
			Road road = (Road) area;
			if (!road.isBlockadesDefined())
				return PassableLevel.UNKNOWN;

			if (!RoadTools.isPassable(road, wi)) {
				if (start == null)
					return PassableLevel.UNPASSABLE;
				
				for (EntityID id : area.getBlockades()) {
					final Blockade block = (Blockade) wi.getEntity(id);
					
					final Shape shape = block.getShape();   // real coordinates
					if (shape == null) 
						return PassableLevel.UNKNOWN;
					
					Line2D pathLine = new Line2D.Double(start.getX(), start.getY(), edgeNode.getX(), edgeNode.getY());
					PathIterator pathIterator = shape.getPathIterator(null);
					double[] c = new double[6];
					double x = 0.0, y = 0.0;
					while (!pathIterator.isDone()) {
						switch (pathIterator.currentSegment(c)) {
						case PathIterator.SEG_MOVETO:
							x = c[0];
							y = c[1];
							break;
						case PathIterator.SEG_LINETO:
							if (pathLine.intersectsLine(x, y, c[0], c[1])) {
								return PassableLevel.UNPASSABLE;
							}
							x = c[0];
							y = c[1];
							break;
						case PathIterator.SEG_QUADTO:
						case PathIterator.SEG_CUBICTO:
						case PathIterator.SEG_CLOSE:
							break;
						}
						pathIterator.next();
					}
				}
			} 
			
			if (RoadTools.isRoadCenterBlocked(road, wi)) {
				return PassableLevel.PARTLT_PASSABLE;
			}
		}
		
		// unblocked roads and buildings
		return PassableLevel.SURE_PASSABLE;
	}

	private Set<EdgeNode> preOwnRoutePath(final Area currentOwnArea, final POVRouter router) {
		Set<EdgeNode> result = new HashSet<EdgeNode>();
		if (preArea != null) {
			final POVPath path = router.search().getAStarPath((Area) wi.getEntity(preArea), 
					currentOwnArea, router.getPfCostFunction(), null);
			if (path != null)  {
				for (POVPath current = path; current.getPrevious() != null; current = current.getPrevious()) {
					final SetPair<PointNode, PointNode> key = 
							new SetPair<>(current.getPoint(), current.getPrevious().getPoint());
					final PassableLevel value = passableMap.get(key);
					if (value == PassableLevel.UNKNOWN ||
							value.ordinal() < PassableLevel.LOGICAL_PASSABLE.ordinal())
						continue;
					passableMap.put(key, PassableLevel.LOGICAL_PASSABLE);
					if (!value.isPassable()) {
						if (current.getPoint() instanceof EdgeNode) {
							result.add((EdgeNode) current.getPoint());
						} else if (current.getPrevious().getPoint() instanceof EdgeNode) {
							result.add((EdgeNode) current.getPrevious().getPoint());
						}
					}
		    	}
			}
		}
		preArea = currentOwnArea.getID();
		return result;
	}

	private Set<EdgeNode> preAgentsRoutePath(final POVRouter router) {
		final Set<EdgeNode> result = new HashSet<EdgeNode>();
		for (Pair<EntityID, EntityID> agentPreArea : preAgentArea) {
			Human human = (Human) wi.getEntity(agentPreArea.first());
			if (!human.isPositionDefined()) 
				continue;
			StandardEntity currentArea = human.getPosition(wi.getRawWorld());
			StandardEntity pastArea = wi.getEntity(agentPreArea.second());
			
			if (currentArea instanceof Area && pastArea != null) {
				POVPath path = router.search().getAStarPath((Area) pastArea, (Area) currentArea,
						router.getPfCostFunction(), null);
				if (path == null)  {
					continue;
				}
				for (POVPath current = path; current.getPrevious() != null; current = current.getPrevious()) {
					final SetPair<PointNode, PointNode> key = 
							new SetPair<>(current.getPoint(), current.getPrevious().getPoint());
					final PassableLevel currentLevel = passableMap.get(key);
					if (currentLevel.ordinal() < PassableLevel.LOGICAL_PASSABLE.ordinal())
						continue;
					passableMap.put(key, PassableLevel.LOGICAL_PASSABLE);
					if (!currentLevel.isPassable()) {
						if (current.getPoint() instanceof EdgeNode) {
							result.add((EdgeNode) current.getPoint());
						} else if (current.getPrevious().getPoint() instanceof EdgeNode) {
							result.add((EdgeNode) current.getPrevious().getPoint());
						}
					}
		    	}
			}
		}
		preAgentArea.clear();
		for (StandardEntity se : WorldTools.getEntitiesOfType(AgentConstants.HUMANOIDS, wi.getRawWorld())) {
			Human human = (Human) se;
			if (human.isPositionDefined()) {
				StandardEntity currentArea = human.getPosition(wi.getRawWorld());
				if (currentArea instanceof Area) { 
					preAgentArea.add(new Pair<EntityID, EntityID>(human.getID(), currentArea.getID()));
				}
			}
		}
		return result;
	}

	public PassableLevel getPassableLevel(final PointNode a, final PointNode b, Point start, WorldInfo wi) {
		if (a instanceof RootNode) {
			return isPassableLine((AreaNode) a, (EdgeNode) b, start, wi);
		}
		if (b instanceof RootNode) {
			return isPassableLine((AreaNode) b, (EdgeNode) a, start, wi);
		}
		if ((a instanceof AreaNode && ((AreaNode) a).getBelong() instanceof Building)
				|| (b instanceof AreaNode && ((AreaNode) b).getBelong() instanceof Building)) {
			return PassableLevel.SURE_PASSABLE;
		}
		SetPair<PointNode, PointNode> key = new SetPair<PointNode, PointNode>(a, b);
		PassableLevel level = passableMap.get(key);
		return level;
	}

}
