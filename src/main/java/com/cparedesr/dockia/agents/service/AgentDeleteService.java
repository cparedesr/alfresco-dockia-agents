package com.cparedesr.dockia.agents.service;

import com.cparedesr.dockia.agents.model.AgentRuntimeInfo;
import com.cparedesr.dockia.agents.service.docker.DockerService;
import com.cparedesr.dockia.agents.service.registry.AgentRegistryService;

import java.util.Properties;

public class AgentDeleteService {

    private AgentRegistryService registryService;
    private DockerService dockerService;
    private Properties globalProperties;

    public void setRegistryService(AgentRegistryService registryService) { this.registryService = registryService; }
    public void setDockerService(DockerService dockerService) { this.dockerService = dockerService; }
    public void setGlobalProperties(Properties globalProperties) { this.globalProperties = globalProperties; }

    public void deleteAgent(String agentId) {
        AgentRuntimeInfo info = registryService.getRuntimeInfoByAgentId(agentId);

        boolean dockerEnabled = Boolean.parseBoolean(globalProperties.getProperty("alfresco.aiagents.docker.enabled", "true"));

        if (dockerEnabled && info.getContainerId() != null && !info.getContainerId().trim().isEmpty()) {
            // idempotente: si no existe el contenedor no debe fallar
            dockerService.remove(info.getContainerId(), true);
        }

        registryService.deleteByAgentId(agentId);
    }
}