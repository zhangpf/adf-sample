package NUDT.centralized;

import adf.agent.communication.standard.bundle.centralized.CommandFire;
import adf.agent.communication.standard.bundle.centralized.CommandScout;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.centralized.CommandPicker;
import adf.component.communication.CommunicationMessage;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class CommandPickerFire extends CommandPicker {

	public CommandPickerFire(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		// TODO Auto-generated constructor stub
	}

	@Override
	public CommandPicker setAllocatorResult(Map<EntityID, EntityID> allocationData) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CommandPicker calc() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<CommunicationMessage> getResult() {
		// TODO Auto-generated method stub
		return null;
	}
	
}