package NUDT.module.complex.utils;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import csu.model.object.CSUBlockade;
import csu.model.object.CSUEdge;
import csu.model.object.CSUEscapePoint;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.Edge;

public class RoadTools {
	
	public static boolean isPassable(Road road)
	{
		if (isAllEdgePassable() || isOneEdgeUnpassable()) {
			
			/*for (CSUBlockade next : getCsuBlockades()) {
				if (next.getPolygon().contains(selfRoad.getX(), selfRoad.getY()))
					return false;
			}*/
			// return true;
			// TODO July 9, 2014  Time: 2:58pm
			return getPassableEdge().size() > 1;      ///why > 
		} else {
			List<CSUBlockade> blockades = new LinkedList<>(getCsuBlockades());
			
			for (CSUEscapePoint next : getEscapePoint(this, 500)) {
				blockades.removeAll(next.getRelateBlockade());
			}
			
			if (blockades.isEmpty())
				return true;
			return false;
		}
	}
	
	public static boolean isAllEdgePassable(Road road) {
		for (Edge next : road.getEdges()) {
			if (!next.isPassable())
				return false;
		}
		return true;
	}
	
	public static boolean isOneEdgeUnpassable(Road road) {
		int count = 0;
		for (Edge next : road.getEdges()) {
			if (!next.isPassable()) 
				count++;
		}
		
		if (count == 1)
			return true;
		else
			return false;
	}
	
	/**
	 * Get all passable edge od this road. If all edge are impassable, then you
	 * are stucked.
	 * 
	 * @return a set of passable edge.
	 */
	public Set<Edge> getPassableEdge(Road road) {
		Set<Edge> result = new HashSet<>();
		
		for (Edge next : road.getEdges()) {
			//?????????????????????????????
			if (next.isPassable() && !next.isBlocked()) {
				result.add(next);
			}
		}
		
		return result;
	}
}
