package com.cparedesr.dockia.agents.service.docker;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;

import java.util.List;
import java.util.Map;

public interface DockerService {

    CreateResult createAndStart(String agentId,
                               String image,
                               Map<String, String> env,
                               Map<String, String> labels,
                               List<AgentDeployRequest.PortMapping> ports);

    void remove(String containerId, boolean force);

    class CreateResult {
        private final String containerId;
        private final String currentState;

        public CreateResult(String containerId, String currentState) {
            this.containerId = containerId;
            this.currentState = currentState;
        }

        public String getContainerId() { return containerId; }
        public String getCurrentState() { return currentState; }
    }
}
