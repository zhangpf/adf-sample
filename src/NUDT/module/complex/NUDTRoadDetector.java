package NUDT.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.RoadDetector;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 选择要探索的Road
 * 1.根据world中信息构造targetAreas和priorityRoads
 * 2.根据消息修改targetAreas和priorityRoads
 * 3.根据pathPlanning模块从targetAreas或priorityRoads中选出一个要探索的Road
 * @author gxd
 */
public class NUDTRoadDetector extends RoadDetector
{

	public NUDTRoadDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		// TODO Auto-generated constructor stub
	}

	@Override
	public RoadDetector calc() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EntityID getTarget() {
		// TODO Auto-generated method stub
		return null;
	}
	
}