package NUDT.utils.extendTools.PoliceForceTools;

import java.awt.Shape;
import java.util.Set;

import org.junit.experimental.theories.Theories;

import adf.agent.info.AgentInfo;
import adf.agent.info.WorldInfo;

import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import NUDT.module.StaticInfoContainer.Entrance;
import NUDT.utils.Ruler;
import NUDT.utils.extendTools.AgentTools;
import NUDT.utils.extendTools.EntityTools;
import NUDT.utils.extendTools.RoadTools;

public class PoliceForceTools {
	/**
	 * 判断一个human是否被困住
	 * 困住的判断依据：
	 *    1.被某个blockade困住
	 *    2.在某个building内，且该building的所有"entrance"被堵住
	 * @return
	 */
	public static boolean judgeStuck(Human human, WorldInfo wi, Entrance entrance)
	{
		Blockade blockade = isLocateInBlockade(human, wi);
		if (blockade == null)
			return false;
		double minDistance = Ruler.getDistanceToBlock(blockade, human.getX(), human.getY());
		
		//如果minDistance很小，说明agent在一个blockade的边界上，此种情况不能算被困住
		//所以只有当minDistance大于一定值，才认为其被困住
		if (minDistance > 500){
			return true;
		}
		
		if (wi.getPosition(human) instanceof Building) {
			Building loc = (Building) wi.getPosition(human);
			
			Set<Road> entrances = entrance.getEntrance(loc);
			int size = entrances.size();
			int count = 0;
			for (Road next : entrance.getEntrance(loc)) {
				Road road = EntityTools.getRoad(next.getID(), wi);
				if (RoadTools.isNeedlessToClear(road, wi))
					continue;
				count++;
			}
			//如果所有入口都被堵住，则该agent被困住
			if (count == size) 
				return true;
		}
		
		return false;
	}
	
	/**
	 * 判断一个platoon agent的xy坐标是否落在一个路障的多边形内
	 * 
	 * @return 当这个plaoon agent的xy坐标落在路障的多边形内返回true。否则，false。
	 */
	public static Blockade isLocateInBlockade(Human human, WorldInfo wi) {
		int x = human.getX();
		int y = human.getY();
		for (EntityID entityID : wi.getChanged().getChangedEntities()){
			StandardEntity se = wi.getEntity(entityID);
			if (se instanceof Blockade){
				Blockade blockade = (Blockade) se;
				Shape s = blockade.getShape();
				if (s != null && s.contains(x, y)) {
					return blockade;
				}
			}
		}
		return null;
	}
}
