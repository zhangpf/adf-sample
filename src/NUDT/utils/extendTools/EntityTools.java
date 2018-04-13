package NUDT.utils.extendTools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import adf.agent.info.AgentInfo;
import adf.agent.info.WorldInfo;
import javolution.util.FastSet;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.EntityID;

public class EntityTools {

	public static Area getArea(EntityID entityID, WorldInfo wi)
	{
		StandardEntity entity = wi.getEntity(entityID);
		if(entity instanceof Area)
		{
			return (Area)entity;
		}
		return null;
	}
	
	public static Building getBuilding(EntityID entityID, WorldInfo wi)
	{
		StandardEntity entity = wi.getEntity(entityID);
		if(entity instanceof Building)
		{
			return (Building)entity;
		}
		return null;
	}
	
	public static Road getRoad(EntityID entityID, WorldInfo wi)
	{
		StandardEntity entity = wi.getEntity(entityID);
		if(entity instanceof Road)
		{
			return (Road)entity;
		}
		return null;
	}
	
	public static Blockade getBlockade(EntityID entityID, WorldInfo wi)
	{
		StandardEntity entity = wi.getEntity(entityID);
		if(entity instanceof Blockade)
			return (Blockade)entity;
		return null;
	}
	
	public static Human getHuman(EntityID entityID, WorldInfo wi)
	{
		StandardEntity entity = wi.getEntity(entityID);
		if(entity instanceof Human)
		{
			return (Human)entity;
		}
		return null;
	}
	
	/**
	 * 得到自身所在entity
	 * @param ai
	 * @param wi
	 * @return
	 */
	public static StandardEntity getSelfPosition(AgentInfo ai, WorldInfo wi)
	{
		return wi.getPosition(ai.me().getID());
	}
	
	public static Pair<Integer, Integer> getSeftLocation(AgentInfo ai, WorldInfo wi)
	{
		return wi.getLocation(ai.me());
	}
	
	/** Convert EntityID list to integer list.*/
	public static List<Integer> entityIdListToIntegerList(List<EntityID> entityIds) {
		List<Integer> returnList = new ArrayList<Integer>();
		for (EntityID entityId: entityIds) {
			returnList.add(entityId.getValue());
		}
		return returnList;
	}
	
	/** Convert integer list to EntityID list.*/
	public static List<EntityID> integerListToEntityIdList(List<Integer> integerIds) {
		List<EntityID> returnList = new ArrayList<EntityID>();
		for (Integer next : integerIds) {
			returnList.add(new EntityID(next.intValue()));
		}
		return returnList;
	}
	
	public static Set<StandardEntity> idToEntities(Collection<EntityID> ids, StandardWorldModel world) {
    	Set<StandardEntity> entities = new FastSet<>();
    	for (EntityID next : ids)  {
    		entities.add(world.getEntity(next));
    	}
    	return entities;
    }
	
    public static Set<EntityID> entityToIds(Collection<StandardEntity> entities) {
    	Set<EntityID> entityIds = new FastSet<>();
    	for (StandardEntity next :entities) {
    		entityIds.add(next.getID());
    	}
    	return entityIds;
    }
	
}
