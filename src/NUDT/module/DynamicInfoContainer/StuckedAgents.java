package NUDT.module.DynamicInfoContainer;

import java.util.HashSet;
import java.util.Set;

import NUDT.module.StaticInfoContainer.Entrance;
import NUDT.utils.extendTools.PoliceForceTools;

import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.AbstractModule;


import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

public class StuckedAgents extends AbstractModule {

	protected Set<EntityID> stuckedAgentList;
	
	private Entrance entrance;
	
	public StuckedAgents(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData, Entrance entrance) {
		super(ai, wi, si, moduleManager, developData);
		
		this.entrance = entrance;
		
		this.stuckedAgentList = new HashSet<>();
	}
	
	@Override
	public AbstractModule calc() {
		
		return null;
	}
	
	public Set<EntityID> getStuckedAgents()
	{
		return this.stuckedAgentList;
	}
	
	public void Update() 
	{
		for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
			StandardEntity entity = this.worldInfo.getEntity(next);
			
			if (entity instanceof AmbulanceTeam || entity instanceof FireBrigade) {
				if (PoliceForceTools.judgeStuck((Human)entity, this.agentInfo, this.worldInfo, this.entrance))
					stuckedAgentList.add(next);
				else
					stuckedAgentList.remove(next);
			}
		}
	}

}
