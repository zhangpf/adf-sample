package NUDT.module.complex.utils.FireBrigadeTools;

import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntity;

import java.awt.geom.Ellipse2D;

import adf.agent.info.WorldInfo;
import adf.agent.info.ScenarioInfo;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Building;


public class FireBrigadeTools {
	
	public static boolean canExtinguish(StandardEntity entity, Building building, WorldInfo wi)
	{
		if(entity instanceof FireBrigade)
		{
			return canExtinguish((FireBrigade)entity, building, wi);
		}
		else 
		{
			return false;
		}
		
	}
	
	public static boolean canExtinguish(FireBrigade fireBrigade, Building building, WorldInfo wi, ScenarioInfo si)
	{
		Pair<Integer, Integer> position = fireBrigade.getLocation(wi.getRawWorld());
		int r = si.getFireExtinguishMaxDistance();
		java.awt.geom.Area range = new java.awt.geom.Area(new Ellipse2D.Double(
				position.first() - r, position.second() - r,
				r * 2, r * 2));
		range.intersect(new java.awt.geom.Area(building.getShape()));
		return !range.isEmpty();
	}
	
}
