package com.cparedesr.dockia.agents.service;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;
import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import com.cparedesr.dockia.agents.service.registry.AgentRegistryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

public class AgentValidationService {

    private AgentRegistryService registryService;
    private NodeService nodeService;
    private Properties globalProperties;

    public void setRegistryService(AgentRegistryService registryService) { this.registryService = registryService; }
    public void setNodeService(NodeService nodeService) { this.nodeService = nodeService; }
    public void setGlobalProperties(Properties globalProperties) { this.globalProperties = globalProperties; }

    private static final Set<String> ALLOWED_LLM_PROVIDERS = Set.of(
            "ollama", "openai", "azure_openai", "anthropic", "custom"
    );

    private static final Set<String> ALLOWED_AUTH_TYPES = Set.of("basic", "bearer", "none");

    public void validateDeployRequest(AgentDeployRequest r) {
        if (r == null) throw new BadRequestException("BODY_REQUIRED", "Request body is required");

        if (!StringUtils.hasText(r.getName())) throw new BadRequestException("NAME_REQUIRED", "name is required");
        if (!StringUtils.hasText(r.getImage())) throw new BadRequestException("IMAGE_REQUIRED", "image is required");
        if (r.getAlfresco() == null) throw new BadRequestException("ALFRESCO_REQUIRED", "alfresco is required");
        if (r.getLlm() == null) throw new BadRequestException("LLM_REQUIRED", "llm is required");

        if (registryService.existsByName(r.getName())) {
            throw new BadRequestException("NAME_ALREADY_EXISTS", "Agent name already exists: " + r.getName());
        }

        // ✅ Allowlist opcional (por defecto NO se aplica)
        boolean allowlistEnabled = Boolean.parseBoolean(
                prop("alfresco.aiagents.image.allowlist.enabled", "false")
        );
        if (allowlistEnabled) {
            String raw = prop("alfresco.aiagents.image.allowlist", "");
            if (!StringUtils.hasText(raw)) {
                throw new BadRequestException("IMAGE_ALLOWLIST_EMPTY", "Allowlist enabled but alfresco.aiagents.image.allowlist is empty");
            }
            boolean allowed = Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .anyMatch(prefix -> r.getImage().startsWith(prefix));

            if (!allowed) {
                throw new BadRequestException("IMAGE_NOT_ALLOWED", "Image not in allowlist");
            }
        }
        // ✅ si allowlistEnabled=false => se permite cualquier imagen

        if (!StringUtils.hasText(r.getAlfresco().getBaseUrl()))
            throw new BadRequestException("ALFRESCO_BASE_URL_REQUIRED", "alfresco.baseUrl is required");

        String authType = (r.getAlfresco().getAuthType() == null) ? "basic" : r.getAlfresco().getAuthType().toLowerCase();
        if (!ALLOWED_AUTH_TYPES.contains(authType)) {
            throw new BadRequestException("ALFRESCO_AUTH_TYPE_INVALID", "alfresco.authType must be basic|bearer|none");
        }

        if ("basic".equals(authType)) {
            if (!StringUtils.hasText(r.getAlfresco().getUsername()))
                throw new BadRequestException("ALFRESCO_USERNAME_REQUIRED", "alfresco.username is required for authType=basic");

            if (r.getAlfresco().getPasswordSecretRef() == null
                    || !StringUtils.hasText(r.getAlfresco().getPasswordSecretRef().getSecretRef())) {
                throw new BadRequestException("ALFRESCO_PASSWORD_SECRET_REQUIRED", "alfresco.passwordSecretRef.secretRef is required for authType=basic");
            }
        }

        boolean hasTarget = StringUtils.hasText(r.getAlfresco().getTargetNodeId()) || StringUtils.hasText(r.getAlfresco().getTargetPath());
        if (!hasTarget) {
            throw new BadRequestException("TARGET_REQUIRED", "alfresco.targetNodeId or alfresco.targetPath is required");
        }

        if (StringUtils.hasText(r.getAlfresco().getTargetNodeId())) {
            NodeRef nr = new NodeRef(r.getAlfresco().getTargetNodeId());
            if (!nodeService.exists(nr)) {
                throw new BadRequestException("TARGET_NODE_NOT_FOUND", "Target node not found: " + r.getAlfresco().getTargetNodeId());
            }
        }

        if (!StringUtils.hasText(r.getLlm().getProvider()))
            throw new BadRequestException("LLM_PROVIDER_REQUIRED", "llm.provider is required");
        if (!ALLOWED_LLM_PROVIDERS.contains(r.getLlm().getProvider()))
            throw new BadRequestException("LLM_PROVIDER_NOT_ALLOWED", "llm.provider not allowed");

        if (("ollama".equals(r.getLlm().getProvider()) || "custom".equals(r.getLlm().getProvider()))
                && !StringUtils.hasText(r.getLlm().getBaseUrl())) {
            throw new BadRequestException("LLM_BASE_URL_REQUIRED", "llm.baseUrl required for provider=" + r.getLlm().getProvider());
        }

        if (!StringUtils.hasText(r.getLlm().getModel()))
            throw new BadRequestException("LLM_MODEL_REQUIRED", "llm.model is required");
        if (!StringUtils.hasText(r.getLlm().getPrompt()))
            throw new BadRequestException("AGENT_PROMPT_REQUIRED", "llm.prompt is required");

        if (r.getPorts() != null) {
            for (AgentDeployRequest.PortMapping p : r.getPorts()) {
                if (p.getContainerPort() < 1 || p.getContainerPort() > 65535)
                    throw new BadRequestException("PORT_INVALID", "containerPort invalid");
                if (p.getHostPort() < 1 || p.getHostPort() > 65535)
                    throw new BadRequestException("PORT_INVALID", "hostPort invalid");
                String proto = p.getProtocol() == null ? "tcp" : p.getProtocol();
                if (!proto.equals("tcp") && !proto.equals("udp"))
                    throw new BadRequestException("PORT_PROTOCOL_INVALID", "protocol must be tcp|udp");
            }
        }
    }

    private String prop(String key, String def) {
        if (globalProperties == null) return def;
        return globalProperties.getProperty(key, def);
    }
}