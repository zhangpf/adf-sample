package NUDT.tactics;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.agent.utils.WorldViewLauncher;
import adf.component.centralized.CommandPicker;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.TargetAllocator;
import adf.component.tactics.TacticsAmbulanceCentre;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.Map;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class NUDTTacticsAmbulanceCentre extends TacticsAmbulanceCentre
{

    private TargetAllocator allocator;
    private CommandPicker picker;
    private Boolean isVisualDebug;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData)
    {
    	/*
    	Collection<StandardEntity> ambulanceCenters = worldInfo.getEntitiesOfType(StandardEntityURN.AMBULANCE_CENTRE);
    	Boolean hasAmbulanceCenters = false;
    	if(ambulanceCenters != null && !ambulanceCenters.isEmpty()) {
    		hasAmbulanceCenters = true;
    	}
    	
    	String tacticsAC_allocator_moduleName = (hasAmbulanceCenters ? "TacticsAmbulanceCentre.TargetAllocator" : "TacticsAmbulanceCentre.DecentralizedTargetAllocator");
        String tacticsAC_allocator_defaultClassName = (hasAmbulanceCenters ? "NUDT.module.complex.NUDTAmbulanceTargetAllocator" : "NUDT.module.complex.NUDTDecentralizedAmbulanceTargetAllocator");
    	*/
    	
        switch (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
            case PRECOMPUTED:
            	/*
            	this.allocator = moduleManager.getModule(
                        tacticsAC_allocator_moduleName,
                        tacticsAC_allocator_defaultClassName);
            	*/
            	this.allocator = moduleManager.getModule(
            			"TacticsAmbulanceCentre.TargetAllocator",
            			"adf.sample.module.complex.NUDTAmbulanceTargetAllocator");
            	this.picker = moduleManager.getCommandPicker(
                        "TacticsAmbulanceCentre.CommandPicker",
                        "adf.sample.centralized.CommandPickerAmbulance");
                break;
            case NON_PRECOMPUTE:
                /*
            	this.allocator = moduleManager.getModule(
                        tacticsAC_allocator_moduleName,
                        tacticsAC_allocator_defaultClassName);
                */
            	this.allocator = moduleManager.getModule(
            			"TacticsAmbulanceCentre.TargetAllocator",
            			"adf.sample.module.complex.NUDTAmbulanceTargetAllocator");
                this.picker = moduleManager.getCommandPicker(
                        "TacticsAmbulanceCentre.CommandPicker",
                        "adf.sample.centralized.CommandPickerAmbulance");
        }
        registerModule(this.allocator);
        registerModule(this.picker);

        this.isVisualDebug = (scenarioInfo.isDebugMode()
                && moduleManager.getModuleConfig().getBooleanValue("VisualDebug", false));
    }

    @Override
    public void think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData)
    {
        modulesUpdateInfo(messageManager);

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }

        Map<EntityID, EntityID> allocatorResult = this.allocator.calc().getResult();
        for (CommunicationMessage message : this.picker.setAllocatorResult(allocatorResult).calc().getResult())
        {
            messageManager.addMessage(message);
        }
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData debugData)
    {
        modulesResume(precomputeData);

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData debugData)
    {
        modulesPreparate();

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
    }
	
}