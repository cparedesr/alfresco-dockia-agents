package com.cparedesr.dockia.agents.service.registry;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;
import com.cparedesr.dockia.agents.model.AgentDetail;
import com.cparedesr.dockia.agents.model.AgentRuntimeInfo;
import com.cparedesr.dockia.agents.model.AgentSummary;

import java.util.List;

public interface AgentRegistryService {

    boolean existsByName(String name);

    void createAgentNode(String agentId,
                         AgentDeployRequest sanitizedRequest,
                         String containerId,
                         String desired,
                         String current,
                         String targetNodeId);

    List<AgentSummary> listAgents(int skipCount, int maxItems);

    AgentDetail getAgentDetailByAgentId(String agentId);

    AgentRuntimeInfo getRuntimeInfoByAgentId(String agentId);

    void deleteByAgentId(String agentId);
}