package com.cparedesr.dockia.agents.model;

public class AgentRuntimeInfo {
    private String agentId;
    private String nodeId;
    private String containerId;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
}