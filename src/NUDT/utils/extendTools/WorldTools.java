package NUDT.utils.extendTools;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import adf.agent.info.AgentInfo;
import adf.agent.info.WorldInfo;
import NUDT.utils.geom.CompositeConvexHull;
import NUDT.utils.geom.PolygonScaler;
import javolution.util.FastSet;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.Property;

/**
 * 扩展WorldInfo类的功能的静态方法集合
 * @author gxd
 *
 */
public class WorldTools {
	
	public static Collection<StandardEntity> getEntitiesOfType(EnumSet<StandardEntityURN> urns, WorldInfo wi) {
		Collection<StandardEntity> res = new HashSet<StandardEntity>();
		for (StandardEntityURN urn : urns) {
			res.addAll(wi.getEntitiesOfType(urn));
		}
		return res;
	}

	public static Set<EntityID> getVisibleEntities(WorldInfo wi) {
		ChangeSet sc = wi.getChanged();
		if (sc != null) {
			return sc.getChangedEntities();
		}

		return null;
	}
	
	/**
	 * 判断entityID所指实体最近发生变化的时间（模拟器运行的第几周期）
	 * @param entityID
	 * @param ai
	 * @param wi
	 * @return
	 */
	public static int getLastChangeTimeOfEntity(EntityID entityID, AgentInfo ai, WorldInfo wi)
	{
		boolean runRollback = wi.isRequestedRollback();
		
		StandardEntity entity = wi.getEntity(entityID);
		
		if(wi.getChanged().getChangedEntities().contains(entityID))
		{
			return ai.getTime();
		}
		if(runRollback)
		{
			for(int i = ai.getTime() - 1; i > -1; i--)
			{
				StandardEntity historyEntity = wi.getEntity(i, entityID);
				if(isEntityChanged(entity, historyEntity))
				{
					return i;
				}
			}
		}
		
		return 0;
	}
	
	/**
	 * 通过属性判断两个实体（当前时刻的entity与若干时刻前的该entity，historyEntity是从worldInfo中的rollback属性中恢复出来的）是否相等
	 * @param currentEntity
	 * @param historyEntity
	 * @return
	 */
	private static boolean isEntityChanged(StandardEntity currentEntity, StandardEntity historyEntity)
	{
		if(currentEntity.getID().getValue() != historyEntity.getID().getValue())
		{
			return false;
		}
		
		//如果属性个数不同，认为发生了变化
		if(currentEntity.getProperties().size() != historyEntity.getProperties().size())
		{
			return true;
		}
		
		for(Property property : currentEntity.getProperties())
		{
			Property historyProperty = historyEntity.getProperty(property.getURN());
			//如果“当前entity”的某个属性“历史entity”没有，或者值不同，则二者不同，直接返回tue
			if(historyEntity == null || !property.getValue().equals(historyProperty.getValue()))
			{
				return true;
			}
		}
		//如果所有属性都相同，返回false
		return false;
	}
	
	/**
     * This method used to find all border building of this world.
     * 
     * @return a set of border building of this world
     */
    public static Set<StandardEntity> findBorderBuilding (double scale, StandardWorldModel world) {
    	CompositeConvexHull convexHull = new CompositeConvexHull();
    	Set<StandardEntity> allEntities = new FastSet<StandardEntity>();
    	for (StandardEntity entity : world) {
			if (entity instanceof Building) {
				allEntities.add(entity);
				Pair<Integer, Integer> location = entity.getLocation(world);
				convexHull.addPoint(location.first(), location.second());
			}
    	}
  
    	Set<StandardEntity> borderBuilding = 
    			PolygonScaler.getMapBorderBuildings(convexHull, allEntities, scale, world);
    	return borderBuilding;
    }
	
}
