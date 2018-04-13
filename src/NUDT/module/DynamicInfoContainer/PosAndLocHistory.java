package NUDT.module.DynamicInfoContainer;

import java.util.Map;
import java.util.TreeMap;

import NUDT.utils.extendTools.EntityTools;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.AbstractModule;
import rescuecore2.misc.Pair;
import rescuecore2.worldmodel.EntityID;

public class PosAndLocHistory extends AbstractModule {

	private Map<Integer, EntityID> positionHistory;
	private Map<Integer, Pair<Integer, Integer>> locationHistory;
	
	public PosAndLocHistory(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		
		this.positionHistory = new TreeMap<Integer, EntityID>();
		this.locationHistory = new TreeMap<Integer, Pair<Integer, Integer>>();
		
	}

	public AbstractModule updateInfo(MessageManager messageManager)
	{
		this.positionHistory.put(this.agentInfo.getTime(), EntityTools.getSelfPosition(this.agentInfo, this.worldInfo).getID());
		this.locationHistory.put(this.agentInfo.getTime(), EntityTools.getSeftLocation(this.agentInfo, this.worldInfo));
		
		return this;
	}
	
	@Override
	public AbstractModule calc() {
		return this;
	}

}
