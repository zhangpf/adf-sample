package NUDT.module.StaticInfoContainer;

import NUDT.module.StaticInfoContainer.Entrance;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.AbstractModule;

/**
 * 存储静态信息的模块，信息只在初始化时构造好，不会随着模拟周期的变化而变化
 * @author gxd
 */
public class StaticInfoContainerModule extends AbstractModule {

	private Entrance entrance;
	public StaticInfoContainerModule(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		
		entrance = new Entrance(wi);
	}

	public Entrance getEntrance() 
	{
		return this.entrance;
	}
	
	@Override
	public AbstractModule calc() {
		
		return null;
	}

}
