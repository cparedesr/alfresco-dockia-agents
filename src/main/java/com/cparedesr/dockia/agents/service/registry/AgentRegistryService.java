package com.cparedesr.dockia.agents.service.registry;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;

public interface AgentRegistryService {
    boolean existsByName(String name);

    void createAgentNode(String agentId,
                         AgentDeployRequest sanitizedRequest,
                         String containerId,
                         String desired,
                         String current,
                         String targetNodeId);
}