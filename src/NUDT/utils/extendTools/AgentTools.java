package NUDT.utils.extendTools;

import java.awt.Point;
import java.awt.Polygon;
import java.util.Set;

import org.jfree.data.statistics.HistogramBin;

import NUDT.utils.Ruler;
import NUDT.utils.Util;

import adf.agent.info.AgentInfo;
import adf.agent.info.WorldInfo;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

public class AgentTools {

	/**
	 * 判断agent能否看到entity
	 * @param entityID
	 * @param wi
	 * @return
	 */
	public static boolean canSee(Entity entity, WorldInfo wi)
	{
		return canSee(entity.getID(), wi);
	}
	
	/**
	 * 判断agent能否看到entityID所指的entity
	 * @param entityID
	 * @param wi
	 * @return
	 */
	public static boolean canSee(EntityID entityID, WorldInfo wi)
	{
		return wi.getChanged().getChangedEntities().contains(entityID);
	}
	
	public static Set<EntityID> getVisibleEntities(WorldInfo wi)
	{
		if(wi.getChanged() != null)
			return wi.getChanged().getChangedEntities();
		return null;
	}
	
	/**
	 * 对于center agent，它控制的实体本身就是一个building，所以它的位置就是它所控制的building实体。
	 * 而对于platton agent，它控制的实体是一个human，human必须站在一个entity上(building, road, 
	 * or ambulance team).
	 * <p>
	 * @return 当前RCR Agent所在的位置
	 */
	public static StandardEntity selfPosition(AgentInfo ai, WorldInfo wi) {
		StandardEntity me = ai.me();// AbstratEntity
		if (me instanceof Human) {
			return wi.getPosition(ai.me().getID());
		}
		return me;
	}
	
	/**
	 * Return this area this agent located in or null when this agent is load by an AT.
	 * @param ai
	 * @param wi
	 * @return
	 */
	public static Area selfAreaPosition(AgentInfo ai, WorldInfo wi)
	{
		StandardEntity location = selfPosition(ai, wi);
		
		if (location instanceof Area)
			return (Area) location;
		
		return null;
	}
	
	/**
	 * 计算(x, y)到blockade的距离，
	 * 如果(x, y)在blockade内部，返回0
	 * @param b
	 * @param x
	 * @param y
	 * @return
	 */
	public static double getDistanceToBlockade(Blockade b, int x, int y)
	{
		Polygon bloc_pol = Util.getPolygon(b.getApexes());
		Point selfL = new Point(x, y);
		
		return Ruler.getDistance(bloc_pol, selfL);
	}
}
