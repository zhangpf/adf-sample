package NUDT.module.DynamicInfoContainer;

import java.util.HashSet;
import java.util.Set;

import NUDT.module.StaticInfoContainer.Entrance;
import NUDT.utils.extendTools.PoliceForceTools.PFLastTaskType.PFClusterLastTaskEnum;
import NUDT.utils.extendTools.PoliceForceTools.PoliceForceTools;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.AbstractModule;
import NUDT.utils.extendTools.PoliceForceTools.PFLastTaskTarget;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

public class PoliceForceHelpInfos extends AbstractModule {

	protected Set<Refuge> coincidentRefuges;
	protected Set<EntityID> visitedRefuges;
	
	protected Set<EntityID> stuckedAgentList;
	protected Set<Human> coincidentBuriedAgent;
	protected Set<EntityID> visitedBuriedAgent;
	
	protected Set<Road> traversalEntranceSet;
	
	protected PFClusterLastTaskEnum clusterLastTaskType;
	/**
	 * The task target of this agent in last cycle.
	 */
	protected PFLastTaskTarget taskTarget;
	
	private Entrance entrance;
	
	public PoliceForceHelpInfos(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData, Entrance entrance) {
		super(ai, wi, si, moduleManager, developData);
		
		this.entrance = entrance;
		
		this.stuckedAgentList = new HashSet<>();
		this.coincidentRefuges = new HashSet<>();
		this.visitedRefuges = new HashSet<>();
		this.coincidentBuriedAgent = new HashSet<>();
		this.visitedBuriedAgent = new HashSet<>();
		
		this.traversalEntranceSet = new HashSet<>();
		
	}

	

	@Override
	public AbstractModule calc() {
		return this;
	}
	
	public Set<EntityID> getStuckedAgents()
	{
		return this.stuckedAgentList;
	}
	
	public Set<Refuge> getCoincidentRefuges()
	{
		return this.coincidentRefuges;
	}
	
	public Set<EntityID> getVisitedRefuges()
	{
		return this.visitedRefuges;
	}
	
	public Set<Human> getCoincidentBuriedAgent()
	{
		return this.coincidentBuriedAgent;
	}
	
	public Set<EntityID> getVisitedBuriedAgent()
	{
		return this.visitedBuriedAgent;
	}
	
	public Set<Road> getTraversalEntranceSet()
	{
		return this.traversalEntranceSet;
	}
	
	public PFClusterLastTaskEnum getClusterLastTaskType()
	{
		return this.clusterLastTaskType;
	}
	
	public void setClusterLastTaskType(PFClusterLastTaskEnum _clusterLastTaskType)
	{
		this.clusterLastTaskType = _clusterLastTaskType;
	}
	
	public PFLastTaskTarget getTaskTarget()
	{
		return this.taskTarget;
	}
	
	public void Update() 
	{
		//update this.stuckedAgentList
		for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
			StandardEntity entity = this.worldInfo.getEntity(next);
			
			if (entity instanceof AmbulanceTeam || entity instanceof FireBrigade) {
				if (PoliceForceTools.judgeStuck((Human)entity, this.worldInfo, this.entrance))
					stuckedAgentList.add(next);
				else
					stuckedAgentList.remove(next);
			}
		}
		
		//
		
	}
	
}
