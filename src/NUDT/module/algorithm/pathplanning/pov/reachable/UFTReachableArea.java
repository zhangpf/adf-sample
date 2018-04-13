package NUDT.module.algorithm.pathplanning.pov.reachable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import NUDT.utils.AgentConstants;
import NUDT.module.algorithm.pathplanning.pov.POVRouter;
import NUDT.module.algorithm.pathplanning.pov.graph.AreaNode;
import NUDT.module.algorithm.pathplanning.pov.graph.EdgeNode;
import NUDT.module.algorithm.pathplanning.pov.graph.PassableDictionary;
import NUDT.utils.extendTools.WorldTools;
import NUDT.utils.UnionFindTree;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

/**
 * Huge map only.
 * 
 * @author utisam
 * 
 */
public class UFTReachableArea {
	
	private UnionFindTree<EntityID> sureReachableTree;
	
	public UFTReachableArea(final WorldInfo wi) {
		
		Collection<StandardEntity> areas = WorldTools.getEntitiesOfType(AgentConstants.AREAS, wi);
		ArrayList<EntityID> ids = new ArrayList<EntityID>(areas.size());
		for (StandardEntity se : areas) {
			ids.add(se.getID());
		}
		sureReachableTree = new UnionFindTree<EntityID>(ids);
	}
	
	public void update(final AgentInfo ai, final ScenarioInfo si, POVRouter router, final Set<EdgeNode> newPassables, WorldInfo wi) {
		if (ai.getTime() > si.getKernelAgentsIgnoreuntil()) {
			updateSureReachable(router, newPassables, wi);
		}
	}
	
	private void updateSureReachable(POVRouter router, Set<EdgeNode> newPassables, WorldInfo wi) {
		//sureReachableTree.resetAll();
		final PassableDictionary passableDic = router.getPassableDic();
		for (EdgeNode edge : newPassables) {
			AreaNode first = null;
			for (AreaNode area : edge.getNeighbours()) {
				if (passableDic.getPassableLevel(area, edge, null, wi).isPassable()) {
					if (first == null) {
						first = area;
					} else {
						sureReachableTree.unite(first.getBelong().getID(), area.getBelong().getID());
					}
				}
			}
		}
	}

	public boolean isSureReachable(EntityID id, EntityID id2) {
		return sureReachableTree.same(id, id2);
	}
}
