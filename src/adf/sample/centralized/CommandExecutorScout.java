package adf.sample.centralized;


import adf.agent.action.common.ActionMove;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.centralized.CommandScout;
import adf.agent.communication.standard.bundle.centralized.MessageReport;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.centralized.CommandExecutor;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.Area;
import rescuecore2.worldmodel.AbstractEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;


/**
 * 执行探索命令
 * @author gxd
 */
public class CommandExecutorScout extends CommandExecutor<CommandScout> {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_SCOUT = 1;

    private PathPlanning pathPlanning;

    private int type;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

    public CommandExecutorScout(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.type = ACTION_UNKNOWN;
        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("CommandExecutorScout.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("CommandExecutorScout.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("CommandExecutorScout.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
        }
    }

    /**
     * 根据收到的Command设置任务/目标信息
     */
    @Override
    public CommandExecutor setCommand(CommandScout command) {
        EntityID agentID = this.agentInfo.getID();
        if(command.isToIDDefined() && (Objects.requireNonNull(command.getToID()).getValue() == agentID.getValue())) {
            EntityID target = command.getTargetID();
            if(target == null) {
                target = this.agentInfo.getPosition();
            }
            this.type = ACTION_SCOUT;
            this.commanderID = command.getSenderID();
            this.scoutTargets = new HashSet<>();
            this.scoutTargets.addAll(
                    worldInfo.getObjectsInRange(target, command.getRange())
                            .stream()
                            .filter(e -> e instanceof Area && e.getStandardURN() != REFUGE)
                            .map(AbstractEntity::getID)
                            .collect(Collectors.toList())
            );
        }
        return this;
    }

    @Override
    public CommandExecutor updateInfo(MessageManager messageManager){
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        
        //命令执行完成，向中心报告，并清空自己的任务
        if(this.isCommandCompleted()) {
            if(this.type != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
                this.type = ACTION_UNKNOWN;
                this.scoutTargets = null;
                this.commanderID = null;
            }
        }
        return this;
    }

    @Override
    public CommandExecutor precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        return this;
    }

    @Override
    public CommandExecutor resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        return this;
    }

    @Override
    public CommandExecutor preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        return this;
    }

    @Override
    public CommandExecutor calc() {
        this.result = null;
        if(this.type == ACTION_SCOUT) {
            if(this.scoutTargets == null || this.scoutTargets.isEmpty()) {
                return this;
            }
            this.pathPlanning.setFrom(this.agentInfo.getPosition());
            this.pathPlanning.setDestination(this.scoutTargets);
            List<EntityID> path = this.pathPlanning.calc().getResult();
            if(path != null) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }

    private boolean isCommandCompleted() {
        if(this.type ==  ACTION_SCOUT) {
            if(this.scoutTargets != null) {
                this.scoutTargets.removeAll(this.worldInfo.getChanged().getChangedEntities());
            }
            return (this.scoutTargets == null || this.scoutTargets.isEmpty());
        }
        return true;
    }
}
