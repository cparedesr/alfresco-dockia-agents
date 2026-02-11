package com.cparedesr.dockia.agents.model;

import java.util.List;
import java.util.Map;

public class AgentDeployRequest {

    private String name;
    private String image;
    private List<PortMapping> ports;
    private AlfrescoConfig alfresco;
    private LlmConfig llm;
    private Map<String, String> env;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public List<PortMapping> getPorts() { return ports; }
    public void setPorts(List<PortMapping> ports) { this.ports = ports; }
    public AlfrescoConfig getAlfresco() { return alfresco; }
    public void setAlfresco(AlfrescoConfig alfresco) { this.alfresco = alfresco; }
    public LlmConfig getLlm() { return llm; }
    public void setLlm(LlmConfig llm) { this.llm = llm; }
    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env; }

    public static class PortMapping {
        private int containerPort;
        private int hostPort;
        private String protocol = "tcp";

        public int getContainerPort() { return containerPort; }
        public void setContainerPort(int containerPort) { this.containerPort = containerPort; }
        public int getHostPort() { return hostPort; }
        public void setHostPort(int hostPort) { this.hostPort = hostPort; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
    }

    public static class SecretRef {
        private String secretRef;
        public String getSecretRef() { return secretRef; }
        public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    }

    public static class AlfrescoConfig {
        private String baseUrl;
        private String authType = "basic"; // basic|bearer|none
        private String username;
        private SecretRef passwordSecretRef;

        private String targetNodeId;
        private String targetPath;

        private int pollingSeconds = 10;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getAuthType() { return authType; }
        public void setAuthType(String authType) { this.authType = authType; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public SecretRef getPasswordSecretRef() { return passwordSecretRef; }
        public void setPasswordSecretRef(SecretRef passwordSecretRef) { this.passwordSecretRef = passwordSecretRef; }
        public String getTargetNodeId() { return targetNodeId; }
        public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }
        public String getTargetPath() { return targetPath; }
        public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
        public int getPollingSeconds() { return pollingSeconds; }
        public void setPollingSeconds(int pollingSeconds) { this.pollingSeconds = pollingSeconds; }
    }

    public static class LlmConfig {
        private String provider;
        private String baseUrl;
        private String model;
        private SecretRef apiKeySecretRef;
        private String prompt;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public SecretRef getApiKeySecretRef() { return apiKeySecretRef; }
        public void setApiKeySecretRef(SecretRef apiKeySecretRef) { this.apiKeySecretRef = apiKeySecretRef; }
        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
    }
}