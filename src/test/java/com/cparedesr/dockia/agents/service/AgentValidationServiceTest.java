package com.cparedesr.dockia.agents.service;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;
import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import com.cparedesr.dockia.agents.service.registry.AgentRegistryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class AgentValidationServiceTest {

    private AgentValidationService validationService;
    private AgentRegistryService registryService;
    private NodeService nodeService;

    @Before
    public void setUp() {
        validationService = new AgentValidationService();
        registryService = mock(AgentRegistryService.class);
        nodeService = mock(NodeService.class);

        validationService.setRegistryService(registryService);
        validationService.setNodeService(nodeService);
    }

    @Test
    public void validRequestPasses() {
        AgentDeployRequest req = validRequest();

        when(registryService.existsByName("agent1")).thenReturn(false);
        when(nodeService.exists(new NodeRef(req.getAlfresco().getTargetNodeId()))).thenReturn(true);

        validationService.validateDeployRequest(req);
    }

    @Test(expected = BadRequestException.class)
    public void duplicateNameFails() {
        AgentDeployRequest req = validRequest();
        when(registryService.existsByName("agent1")).thenReturn(true);

        validationService.validateDeployRequest(req);
    }

    @Test(expected = BadRequestException.class)
    public void missingTargetFails() {
        AgentDeployRequest req = validRequest();
        req.getAlfresco().setTargetNodeId(null);
        req.getAlfresco().setTargetPath(null);

        when(registryService.existsByName("agent1")).thenReturn(false);

        validationService.validateDeployRequest(req);
    }

    @Test(expected = BadRequestException.class)
    public void targetNodeNotFoundFails() {
        AgentDeployRequest req = validRequest();

        when(registryService.existsByName("agent1")).thenReturn(false);
        when(nodeService.exists(new NodeRef(req.getAlfresco().getTargetNodeId()))).thenReturn(false);

        validationService.validateDeployRequest(req);
    }

    @Test(expected = BadRequestException.class)
    public void basicAuthRequiresUsername() {
        AgentDeployRequest req = validRequest();
        req.getAlfresco().setUsername(null);

        when(registryService.existsByName("agent1")).thenReturn(false);
        when(nodeService.exists(new NodeRef(req.getAlfresco().getTargetNodeId()))).thenReturn(true);

        validationService.validateDeployRequest(req);
    }

    @Test(expected = BadRequestException.class)
    public void invalidPortFails() {
        AgentDeployRequest req = validRequest();
        AgentDeployRequest.PortMapping p = new AgentDeployRequest.PortMapping();
        p.setContainerPort(70000);
        p.setHostPort(18080);
        req.setPorts(java.util.List.of(p));

        when(registryService.existsByName("agent1")).thenReturn(false);
        when(nodeService.exists(new NodeRef(req.getAlfresco().getTargetNodeId()))).thenReturn(true);

        validationService.validateDeployRequest(req);
    }

    private AgentDeployRequest validRequest() {
        AgentDeployRequest req = new AgentDeployRequest();
        req.setName("agent1");
        req.setImage("registry.tuorg.com/agents/summarizer:1.0.0");

        AgentDeployRequest.AlfrescoConfig a = new AgentDeployRequest.AlfrescoConfig();
        a.setBaseUrl("http://localhost:8080/alfresco");
        a.setAuthType("basic");
        a.setUsername("svc_ai");
        AgentDeployRequest.SecretRef sr = new AgentDeployRequest.SecretRef();
        sr.setSecretRef("prop:alfresco.aiagents.secret.svc_ai_password");
        a.setPasswordSecretRef(sr);
        a.setTargetNodeId("workspace://SpacesStore/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        a.setPollingSeconds(10);
        req.setAlfresco(a);

        AgentDeployRequest.LlmConfig llm = new AgentDeployRequest.LlmConfig();
        llm.setProvider("ollama");
        llm.setBaseUrl("http://ollama:11434");
        llm.setModel("llama3.1");
        llm.setPrompt("hola");
        req.setLlm(llm);

        return req;
    }
}