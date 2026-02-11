package com.cparedesr.dockia.agents.service;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;
import com.cparedesr.dockia.agents.model.AgentDeployResponse;
import com.cparedesr.dockia.agents.service.docker.DockerService;
import com.cparedesr.dockia.agents.service.registry.AgentRegistryService;
import com.cparedesr.dockia.agents.service.repo.AlfrescoFolderService;
import com.cparedesr.dockia.agents.service.secrets.SecretsService;
import org.springframework.util.StringUtils;

import java.util.*;

public class AgentDeploymentService {

    private AlfrescoFolderService folderService;
    private SecretsService secretsService;
    private DockerService dockerService;
    private AgentRegistryService registryService;
    private Properties globalProperties;

    public void setFolderService(AlfrescoFolderService folderService) { this.folderService = folderService; }
    public void setSecretsService(SecretsService secretsService) { this.secretsService = secretsService; }
    public void setDockerService(DockerService dockerService) { this.dockerService = dockerService; }
    public void setRegistryService(AgentRegistryService registryService) { this.registryService = registryService; }
    public void setGlobalProperties(Properties globalProperties) { this.globalProperties = globalProperties; }

    public AgentDeployResponse deploy(AgentDeployRequest req) {

        String agentId = "agent-" + UUID.randomUUID();

        String resolvedTargetNodeId = folderService.ensureAndResolveNodeId(
                req.getAlfresco().getTargetNodeId(),
                req.getAlfresco().getTargetPath()
        );

        String authType = (req.getAlfresco().getAuthType() == null) ? "basic" : req.getAlfresco().getAuthType().toLowerCase();

        String alfPass = null;
        if ("basic".equals(authType)) {
            alfPass = secretsService.resolve(req.getAlfresco().getPasswordSecretRef().getSecretRef());
        }

        String llmKey = (req.getLlm().getApiKeySecretRef() != null && StringUtils.hasText(req.getLlm().getApiKeySecretRef().getSecretRef()))
                ? secretsService.resolve(req.getLlm().getApiKeySecretRef().getSecretRef())
                : null;

        Map<String,String> env = new HashMap<>();
        env.put("ALFRESCO_BASE_URL", req.getAlfresco().getBaseUrl());
        env.put("ALFRESCO_AUTH_TYPE", authType);
        if (StringUtils.hasText(req.getAlfresco().getUsername())) env.put("ALFRESCO_USERNAME", req.getAlfresco().getUsername());
        if (alfPass != null) env.put("ALFRESCO_PASSWORD", alfPass);

        env.put("ALFRESCO_TARGET_NODE_ID", resolvedTargetNodeId);
        env.put("POLLING_SECONDS", String.valueOf(req.getAlfresco().getPollingSeconds()));

        env.put("LLM_PROVIDER", req.getLlm().getProvider());
        if (StringUtils.hasText(req.getLlm().getBaseUrl())) env.put("LLM_BASE_URL", req.getLlm().getBaseUrl());
        env.put("LLM_MODEL", req.getLlm().getModel());
        if (llmKey != null) env.put("LLM_API_KEY", llmKey);

        env.put("AGENT_PROMPT", req.getLlm().getPrompt());

        if (req.getEnv() != null) env.putAll(req.getEnv());

        Map<String,String> labels = Map.of(
                "com.cparedesr.dockia.agentId", agentId,
                "com.cparedesr.dockia.name", req.getName(),
                "com.cparedesr.dockia.targetNodeId", resolvedTargetNodeId
        );

        DockerService.CreateResult created = dockerService.createAndStart(agentId, req.getImage(), env, labels, req.getPorts());

        AgentDeployRequest sanitized = sanitize(req, resolvedTargetNodeId);
        registryService.createAgentNode(agentId, sanitized, created.getContainerId(), "running", created.getCurrentState(), resolvedTargetNodeId);

        return new AgentDeployResponse(
                agentId,
                req.getName(),
                "running",
                created.getCurrentState(),
                "/alfresco/api/-default-/public/ai-agents/versions/1/agents/" + agentId + "/status"
        );
    }

    private AgentDeployRequest sanitize(AgentDeployRequest in, String resolvedTargetNodeId) {
        AgentDeployRequest out = new AgentDeployRequest();
        out.setName(in.getName());
        out.setImage(in.getImage());
        out.setPorts(in.getPorts());
        out.setEnv(in.getEnv());

        AgentDeployRequest.AlfrescoConfig a = new AgentDeployRequest.AlfrescoConfig();
        a.setBaseUrl(in.getAlfresco().getBaseUrl());
        a.setAuthType(in.getAlfresco().getAuthType());
        a.setUsername(in.getAlfresco().getUsername());
        a.setPasswordSecretRef(in.getAlfresco().getPasswordSecretRef()); // secretRef OK
        a.setTargetNodeId(resolvedTargetNodeId);
        a.setTargetPath(in.getAlfresco().getTargetPath());
        a.setPollingSeconds(in.getAlfresco().getPollingSeconds());
        out.setAlfresco(a);

        AgentDeployRequest.LlmConfig l = new AgentDeployRequest.LlmConfig();
        l.setProvider(in.getLlm().getProvider());
        l.setBaseUrl(in.getLlm().getBaseUrl());
        l.setModel(in.getLlm().getModel());
        l.setApiKeySecretRef(in.getLlm().getApiKeySecretRef());
        l.setPrompt(in.getLlm().getPrompt());
        out.setLlm(l);

        return out;
    }
}