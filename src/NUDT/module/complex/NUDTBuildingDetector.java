package NUDT.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.AbstractModule;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.BuildingDetector;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.standard.entities.StandardEntityConstants.Fieryness;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import NUDT.module.complex.utils.FireBrigadeTools.EnergyFlow;
import NUDT.module.complex.utils.FireBrigadeTools.FireBrigadeTools;
import NUDT.module.complex.utils.WorldTools;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class NUDTBuildingDetector extends BuildingDetector
{

	private EntityID result;
	private EnergyFlow energyFlow;
	
	public NUDTBuildingDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		
		switch (si.getMode())
        {
            case PRECOMPUTATION_PHASE:
                this.energyFlow = moduleManager.getModule("NUDTBuildingDetector.EnergyFlow", "NUDT.module.complex.utils.EnergyFlow");
                break;
            case PRECOMPUTED:
            	this.energyFlow = moduleManager.getModule("NUDTBuildingDetector.EnergyFlow", "NUDT.module.complex.utils.EnergyFlow");
                break;
            case NON_PRECOMPUTE:
                this.energyFlow = moduleManager.getModule("NUDTBuildingDetector.EnergyFlow", "NUDT.module.complex.utils.EnergyFlow");
                break;
        }
		
		registerModule(this.energyFlow);
	}

	@Override
	public BuildingDetector calc() 
	{	
		EntityID tmp = this.selectOneBuildingFromViewsightForExtinguishing().getID();
		if(tmp != null)
		{
			this.result = tmp;
			return this;
		}
		tmp = this.selectOneBuildingFromWorldForExtinguishing().getID();
		if(tmp != null)
		{
			this.result = tmp;
			return this;
		}
		tmp = this.selectOneBuildingInRangeForExtinguishingWhenStucked().getID();
		if(tmp != null)
		{
			this.result = tmp;
			return this;
		}
		tmp = this.selectOneBuildingForMovingTo().getID();
		if(tmp != null)
		{
			this.result = tmp;
			return this;
		}
		this.result = null;
		return this;
	}
	
	/**
	 * 从能看到的建筑中，挑选一个着火的并且最容易灭火的建筑
	 * @return
	 */
	private Building selectOneBuildingFromViewsightForExtinguishing() {
		
		
		/* the building with minimum extinguish difficulty.*/
		Building minDifficultyBuilding = null;
		/* the minimum extinguish difficulty*/
		double minDifficulty = Integer.MAX_VALUE;
		/* the energy minDifficultyBuilding released to outside world*/
		double minDifficultyAffect = 0.0;
		
		for (EntityID id : this.worldInfo.getFireBuildingIDs()) {
			StandardEntity se = this.worldInfo.getEntity(id);
			if (se instanceof Building){
				Building building = (Building) se;
				/* total area of this building*/
				double area = (building.isTotalAreaDefined()) ? building.getTotalArea() : 1.0;
				/* the energy gained from outside world of this building*/
				double affected = this.energyFlow.getIn(building);
				/* the extinguish difficulty of this building*/
				double difficulty = area * affected;
				
				if (this.worldInfo.getFireBuildings().contains(building) && difficulty < minDifficulty) {
					minDifficultyBuilding = building;
					minDifficulty = difficulty;//
					minDifficultyAffect = this.energyFlow.getOut(building);
				}else if (Math.abs(minDifficulty - difficulty) < 500.0) {
					/* 
					 * If two building has colser extinguish difficulty, then compare the energy they
					 * released to outside world. The building which release more energy is the new
					 * minimum extinguish difficulty building.
					 */
					double affect = this.energyFlow.getOut(building);
					if (minDifficultyAffect < affect) {
						minDifficultyBuilding = building;
						minDifficulty = difficulty;
						minDifficultyAffect = this.energyFlow.getOut(building);
					}
				}
			}
		}
		
		return minDifficultyBuilding;
	}
	
	/**
	 * 该方法尝试从世界中挑选一个自己能“够得着”的着火的并且灭火难度小的建筑
	 * @return
	 */
	private Building selectOneBuildingFromWorldForExtinguishing() 
	{
		Building minDifficultyBuilding = null;
		double minDifficulty = Integer.MAX_VALUE;
		double minDifficultyAffect = 0.0;
		
		for (Building building : this.worldInfo.getFireBuildings()) 
		{
			if (!FireBrigadeTools.canExtinguish(this.agentInfo.me(), building, this.worldInfo)
					|| this.agentInfo.getTime() - WorldTools.getLastChangeTimeOfEntity(building.getID(), this.agentInfo, this.worldInfo) >= 3) {
				continue;
			}
			if (building.isFierynessDefined() && building.getFierynessEnum() == Fieryness.INFERNO) {
				continue;
			}
			double area = (building.isTotalAreaDefined()) ? building.getTotalArea() : 1.0;
			double affected = this.energyFlow.getIn(building);
			double difficulty = area * affected;
			
			if (this.worldInfo.getFireBuildings().contains(building) && difficulty < minDifficulty) {
				minDifficultyBuilding = building;
				minDifficulty = difficulty;
				minDifficultyAffect = this.energyFlow.getOut(building);
			} else if (Math.abs(minDifficulty - difficulty) < 500.0) {
				double affect = this.energyFlow.getOut(building);
				if (minDifficultyAffect < affect) {
					minDifficultyBuilding = building;
					minDifficulty = difficulty;
					minDifficultyAffect = this.energyFlow.getOut(building);
				}
			}
		}
		
		return minDifficultyBuilding;
	}
	
	/**
	 * 当消防员被困住时，从自己的“灭火范围”内选一个建筑
	 * @return
	 */
	private Building selectOneBuildingInRangeForExtinguishingWhenStucked() 
	{
		final int extinguishableDistance = this.scenarioInfo.getFireExtinguishMaxDistance();
		for (StandardEntity se : this.worldInfo.getObjectsInRange(this.agentInfo.me(), extinguishableDistance)) {
			if (se instanceof Building) {
				Building building = (Building) se;
				if (building.getFieryness() > 0 && building.getFieryness() < 4) {
					System.out.println("Agent: " + this.agentInfo.me() + " is error extinguishing in time: " 
						     + this.agentInfo.getTime() + " ----- class:CsuOldBasedActionStrategy, method: errorExtinguish()");
					return building;
				}
			}
		}
		System.out.println("In time: " + this.agentInfo.getTime() + " Agent: " + this.agentInfo.me() + " can not " +
				"error extinguish and leave.  ----- class:CsuOldBasedActionStrategy, method: errorExtinguish()");
		// underlyingAgent.move(underlyingAgent.getCannotLeaveBuildingEntrance());
		return null;
	}
	
	private Building selectOneBuildingForMovingTo()
	{
		if (this.worldInfo.getFireBuildings().isEmpty()){
			System.out.println("Agent: " + this.agentInfo.me() + " has no burning building in his world model " +
				"----- time: " + this.agentInfo.getTime() + ", class: CsuOldBasedActionStrategy, method: moveToFires()");
			return null;
		}
		
		Building minValueBuilding = null;
		double minValue = Integer.MAX_VALUE;
		
		for (Building building : this.worldInfo.getFireBuildings()) {
			final double affect = this.energyFlow.getOut(building);
			final double distance = this.worldInfo.getDistance(building, this.agentInfo.me());
			final double value = affect * distance;
			
			if (value < minValue) {
				minValueBuilding = building;
				minValue = value;
			}
		}
		if (minValueBuilding != null) {
			System.out.println("Agent: " + this.agentInfo.me()+ " moving to a burning building in his world model " +
					"----- time: " + this.agentInfo.getTime() + ", class: CsuOldBasedActionStrategy, method: moveToFires()");
			return minValueBuilding;
		}
		System.out.println("In time: " + this.agentInfo.getTime() + " Agent: " + this.agentInfo.me() + " can not find " +
				"a burning building to move. ----- class: CsuOldBasedActionStrategy, method: moveToFires()");
		return null;
	}

	@Override
	public EntityID getTarget() {
		return this.result;
	}
	
}