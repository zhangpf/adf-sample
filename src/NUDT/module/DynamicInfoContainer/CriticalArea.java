package NUDT.module.DynamicInfoContainer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.AbstractModule;
import adf.component.module.algorithm.Clustering;
import NUDT.module.algorithm.pathplanning.pov.POVRouter;
import NUDT.utils.extendTools.WorldTools;
import NUDT.utils.AgentConstants;

import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

/**
 * 如果WorldModel可以扩展，可把这部分放入StandardWorldModel
 * We divide roads into three different kinds. One is called the "entrance", for
 * this kind roads are connected to the buildings, so if you'd like to go into
 * the buildings you must pass these roads. The second one is called "avenue", for
 * they can be combined into a long street. And the rest is the "cross", these
 * roads are connected to more than three roads.
 * <p>
 * The cross of two long street often plays important role in traffic system, so
 * we them as critical areas. Also, refuges are critical area, too.
 * <p>
 * Date: Mar 10, 2014 Time: 2:03 am  improved by appreciation-csu
 * 
 * @author utisam
 */
public class CriticalArea extends AbstractModule{
	private List<Area> criticalAreas = new ArrayList<Area>();
	
	private List<EntityID> criticalAreaIds = new ArrayList<>();

	public CriticalArea(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		
		//遍历所有area，找到邻居都是Road的area
		AREAMARK: for (StandardEntity se : WorldTools.getEntitiesOfType(AgentConstants.ROADS, this.worldInfo)) {
			Area area = (Area) se;
			List<Edge> edges = area.getEdges();
			//如果边数小于3，构不成多边形，则忽略这个area
			if (edges.size() < 3)
				continue;
			for (Edge edge : edges) {
				//如果有一条边没有邻居，则忽略这个area
				if (!edge.isPassable())
					continue AREAMARK;
				//如果有一条边的邻居是building，则忽略这个area
				if (this.worldInfo.getEntity(edge.getNeighbour()) instanceof Building)
					continue AREAMARK;
			}
			
			criticalAreas.add(area);
			criticalAreaIds.add(area.getID());
		}
		
		// remove neighbour of entrances
		for (Iterator<Area> itor = criticalAreas.iterator(); itor.hasNext();) {
			Area area = (Area)itor.next();
			List<EntityID> neighbours = area.getNeighbours();
			CRITICAL_MARK: for (EntityID entityId : neighbours) {
				Area neighbour = (Area) this.worldInfo.getEntity(entityId);
				List<EntityID> n_neighbours = neighbour.getNeighbours();
				//如果”邻居的邻居“数小于等于2的话，那么这个area有可能是"entrance"
				if (n_neighbours.size() <= 2) {
					for (EntityID next : n_neighbours) {
						if (this.worldInfo.getEntity(next) instanceof Building) {
							criticalAreaIds.remove(area.getID());
							itor.remove();
							break CRITICAL_MARK;
						}
					}
				}
				//”邻居的边“的数量与”邻居的邻居“的数量相等的话
				else if (neighbour.getEdges().size() == n_neighbours.size()) {
					criticalAreaIds.remove(area.getID());
					itor.remove();
					break CRITICAL_MARK;
				}
			}
		}
	}
	
	private final int MAX_SEND_SIZE = 7; 
	private int SEND_SIZE_BIT = 3;//BitUtil.needBitSize(MAX_SEND_SIZE);
	private List<Area> sendRemovedAreas = new ArrayList<Area>();

	/**
	 * 判断哪些area能够到达，把能够到达的area从this.criticalAreas中移除，
	 * 并且放入this.sendRemovedAreas中通过消息告知其他agent
	 * @param router
	 */
	public void update(POVRouter router) {
		sendRemovedAreas.clear();
		for (Iterator<Area> it = criticalAreas.iterator(); it.hasNext();) {
			Area area = (Area) it.next();
			
			if (router.isSureReachable(area)) {
				if (sendRemovedAreas.size() <= MAX_SEND_SIZE) {
					sendRemovedAreas.add(area);
				}
				criticalAreaIds.remove(area.getID());
				it.remove();
			}
		}
	}
	
	/**
	 * Need some Message reader and writer.
	 */

	
	public int size() {
		return criticalAreas.size();
	}

	public Area get(int index) {
		return (Area) criticalAreas.get(index);
	}

	public List<Area> getAreas() {
		return criticalAreas;
	}
	
	public boolean isCriticalArea(Area area) {
		return this.isCriticalArea(area.getID());
	}
	
	public boolean isCriticalArea(EntityID area) {
		return this.criticalAreaIds.contains(area);
	}

	
	/**
	 * 在外层容器中手动调用update(router)
	 * 因为外层容器拥有criticalArea和router
	 * @param messageManager
	 * @return
	 */
	@Override
    public CriticalArea updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        //每个周期只update一次
        if(this.getCountUpdateInfo() > 1) { return this; }
        
        return this;
    }
	
	@Override
	public AbstractModule calc() {
		// TODO Auto-generated method stub
		return null;
	}
}
