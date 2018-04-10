package NUDT.module.algorithm.pathplanning.pov;

import java.awt.Point;
import java.util.Map;

import NUDT.module.algorithm.pathplanning.pov.graph.AreaNode;
import NUDT.module.algorithm.pathplanning.pov.graph.EdgeNode;
import NUDT.module.algorithm.pathplanning.pov.graph.PassableDictionary;
import NUDT.module.algorithm.pathplanning.pov.graph.PointNode;
import adf.agent.info.ScenarioInfo;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Road;
import rescuecore2.worldmodel.EntityID;

class CostFunctionFactory {
		
	/*以下返回的func均没有考虑出与入的代价差别，
	  例如: 从一个着火的建筑出来的代价应该小于进入一个着火的建筑的代价
	  */
	
	static CostFunction normal(final PassableDictionary passableDic) {
		return new CostFunction() {
			@Override
			public double cost(final PointNode from, final PointNode to, Point startPoint) {
				double distance = from.distance(to);
				AreaNode areaNode;
				EdgeNode edgeNode;
				if (from instanceof AreaNode) {
					areaNode = (AreaNode) from;
					edgeNode = (EdgeNode) to;
				} else /* if (to instanceof AreaNode) */ {
					edgeNode = (EdgeNode) from;
					areaNode = (AreaNode) to;
				}
				
				//edge太短，agent等无法通过，所以要将通过此边的代价调高
				if (edgeNode.isTooSmall()) ///why
					return distance * 100000000.0;
				
				Area area = areaNode.getBelong();
				if (area instanceof Building) {
					if (((Building) area).isOnFire()) {
						return distance * 1000.0;
					} else {
						return distance * 1.5;
					}
				}
				if (area instanceof Road) {
					switch (passableDic.getPassableLevel(areaNode, edgeNode, startPoint)) {
					case SURE_PASSABLE:
					case COMMUNICATION_PASSABLE:
						return distance;
					case PARTLT_PASSABLE:
						return distance * 2.0;
					case LOGICAL_PASSABLE:
						return distance;
					case UNKNOWN:
						return distance;
					case UNPASSABLE: 
					{
						return distance * 100000000.0;
					}
					}
				}
				return distance;
			}
		};
	}
	
	static CostFunction strict(final PassableDictionary passableDic) {
		return new CostFunction() {
			@Override
			public double cost(final PointNode from, final PointNode to, Point startPoint) {
				double distance = from.distance(to);
				AreaNode areaNode;
				EdgeNode edgeNode;
				if (from instanceof AreaNode) {
					areaNode = (AreaNode) from;
					edgeNode = (EdgeNode) to;
				} else /* if (to instanceof AreaNode) */ {
					edgeNode = (EdgeNode) from;
					areaNode = (AreaNode) to;
				}
				
				if (edgeNode.isTooSmall())
					return distance * 100000000.0;
				
				Area area = areaNode.getBelong();
				if (area instanceof Building) {
					if (((Building) area).isOnFire()) {
						return distance * 100000.0;
					} else {
						return distance * 1.5;
					}
				}
				if (area instanceof Road) {
					switch (passableDic.getPassableLevel(areaNode, edgeNode, startPoint)) {
					case SURE_PASSABLE:
					case COMMUNICATION_PASSABLE:
						return distance;	
					case PARTLT_PASSABLE:
						return distance * 2.0;
					case LOGICAL_PASSABLE:
						return distance * 3.0;
					case UNKNOWN:
						return distance * 4.0;
					case UNPASSABLE:
						return Double.POSITIVE_INFINITY;
					}
				}
				return distance;
			}
		};
	}
	
	static CostFunction search(final PassableDictionary passableDic) {
		return new CostFunction() {
			@Override
			public double cost(final PointNode from, final PointNode to, Point startPoint) {
				double distance = from.distance(to);
				AreaNode areaNode;
				EdgeNode edgeNode;
				if (from instanceof AreaNode) {
					areaNode = (AreaNode) from;
					edgeNode = (EdgeNode) to;
				} else /* if (to instanceof AreaNode) */ {
					edgeNode = (EdgeNode) from;
					areaNode = (AreaNode) to;
				}
				
				if (edgeNode.isTooSmall())
					return distance * 100000000.0;
				
				Area area = areaNode.getBelong();
				if (area instanceof Building) {
					if (((Building) area).isOnFire()) {
						return distance * 1000.0;
					} else {
						return distance * 1.5;
					}
				}
				if (area instanceof Road) {
					switch (passableDic.getPassableLevel(areaNode, edgeNode, startPoint)) {
					case SURE_PASSABLE:
						return distance * 1.5;
					case PARTLT_PASSABLE:
						return distance * 2.0;
					case COMMUNICATION_PASSABLE:
						return distance;		
					case LOGICAL_PASSABLE:
						return distance * 10.0;
					case UNKNOWN:
						return distance * 0.75;
					case UNPASSABLE:
						return distance * 1000000.0;
					}
				}
				return distance;
			}
		};
	}
	
	static CostFunction fb(final ScenarioInfo si, 
			final PassableDictionary passableDic, final Building dest) {
		return new CostFunction() {
			@Override
			public double cost(final PointNode from, final PointNode to, Point startPoint) {
				double distance = from.distance(to);
				AreaNode areaNode;
				EdgeNode edgeNode;
				if (from instanceof AreaNode) {
					areaNode = (AreaNode) from;
					edgeNode = (EdgeNode) to;
				} else /* if (to instanceof AreaNode) */ {
					edgeNode = (EdgeNode) from;
					areaNode = (AreaNode) to;
				}
				
				if (edgeNode.isTooSmall())
					return distance * 100000000.0;
				
				Area area = areaNode.getBelong();
				if (area instanceof Building) {
					if (((Building) area).isOnFire()) {
						return distance * 100.0;
					} else {
						return distance * 1.5;
					}
				}
				if (to.distance(dest.getX(), dest.getY()) < si.getFireExtinguishMaxDistance()) {
					return distance;
				}
				if (area instanceof Road) {
					switch (passableDic.getPassableLevel(areaNode, edgeNode, startPoint)) {
					case SURE_PASSABLE:
					case COMMUNICATION_PASSABLE:
						return distance;	
					case PARTLT_PASSABLE:
						return distance * 2.0;
					case LOGICAL_PASSABLE:
						return distance;
					case UNKNOWN:
						return distance;
					case UNPASSABLE:
						int extinguishable = si.getFireExtinguishMaxDistance();
						double cost = distance * 1000.0 * to.distance(dest.getX(), dest.getY());
						return Math.max(cost / extinguishable, 1);  ///why
					}
				}
				return distance;
			}
		};
	}

	static CostFunction pf() {
		return new CostFunction() {
			@Override
			public double cost(PointNode from, PointNode to, Point startPoint) {
				double distance = from.distance(to);
				AreaNode areaNode;
				EdgeNode edgeNode;
				if (from instanceof AreaNode) {
					areaNode = (AreaNode) from;
					edgeNode = (EdgeNode) to;
				} else /* if (to instanceof AreaNode) */ {
					edgeNode = (EdgeNode) from;
					areaNode = (AreaNode) to;
				}
				
				if (edgeNode.isTooSmall())
					return distance * 100000000.0;
				
				Area area = areaNode.getBelong();
				if (area instanceof Building) {
					if (((Building) area).isOnFire()) {
						return distance * 1000.0;
					} else {
						return distance * 1.5;
					}
				}
				return distance;
			}
		};
	}
	
	static CostFunction at(final PassableDictionary passableDic, final Map<EntityID, Double> minStaticCost) {
		return new CostFunction() {
			@Override
			public double cost(final PointNode from, final PointNode to, Point startPoint) {
				double distance = from.distance(to);
				AreaNode areaNode;
				EdgeNode edgeNode;
				if (from instanceof AreaNode) {
					areaNode = (AreaNode) from;
					edgeNode = (EdgeNode) to;
				}
				else /* if (to instanceof AreaNode) */ {
					edgeNode = (EdgeNode) from;
					areaNode = (AreaNode) to;
				}
				
				if (edgeNode.isTooSmall())
					return distance * 100000000.0;
				
				Area area = areaNode.getBelong();
				if (area instanceof Building) {
					if (((Building) area).isOnFire()) {
						return distance * 100000.0;
					}
				}
				if (area instanceof Road) {
					switch (passableDic.getPassableLevel(areaNode, edgeNode, startPoint)) {
					case SURE_PASSABLE:
					case COMMUNICATION_PASSABLE:
						return distance;
					case PARTLT_PASSABLE:
						return distance * 2.0;
					case LOGICAL_PASSABLE:
						return distance;
					case UNKNOWN:
						return distance;
					case UNPASSABLE:
						return distance * 100000000.0;
					}
				}
				Double staticCost = minStaticCost.get(area.getID());
				if (staticCost != null) {
					distance += staticCost;
				}
				return distance;
			}
		};
	}
}
