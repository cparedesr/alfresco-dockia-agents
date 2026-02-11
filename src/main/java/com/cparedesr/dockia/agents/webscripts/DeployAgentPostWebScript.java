package com.cparedesr.dockia.agents.webscripts;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;
import com.cparedesr.dockia.agents.model.AgentDeployResponse;
import com.cparedesr.dockia.agents.service.AgentDeploymentService;
import com.cparedesr.dockia.agents.service.AgentValidationService;
import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
// import org.alfresco.repo.web.scripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

import java.util.HashMap;
import java.util.Map;

public class DeployAgentPostWebScript extends DeclarativeWebScript {

    private final ObjectMapper mapper = new ObjectMapper();

    private AgentValidationService validationService;
    private AgentDeploymentService deploymentService;

    public void setValidationService(AgentValidationService validationService) {
        this.validationService = validationService;
    }

    public void setDeploymentService(AgentDeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {

        Map<String, Object> model = new HashMap<>();

        try {
            if (req.getContent() == null) {
                throw new BadRequestException("BODY_REQUIRED", "Request body is required");
            }

            String json = req.getContent().getContent();
            if (json == null || json.trim().isEmpty()) {
                throw new BadRequestException("BODY_REQUIRED", "Request body is required");
            }

            AgentDeployRequest deployRequest = mapper.readValue(json, AgentDeployRequest.class);

            validationService.validateDeployRequest(deployRequest);

            AgentDeployResponse response = deploymentService.deploy(deployRequest);

            status.setCode(Status.STATUS_CREATED);

            model.put("data", response);
            model.put("location",
                    req.getServiceContextPath()
                            + "/api/-default-/public/ai-agents/versions/1/agents/"
                            + response.getAgentId());

            return model;

        } catch (BadRequestException e) {
            status.setCode(Status.STATUS_BAD_REQUEST);
            model.put("error", Map.of(
                    "statusCode", 400,
                    "code", e.getCode(),
                    "message", e.getMessage()
            ));
            return model;

        } catch (Exception e) {
            status.setCode(Status.STATUS_INTERNAL_SERVER_ERROR);
            model.put("error", Map.of(
                    "statusCode", 500,
                    "code", "INTERNAL_ERROR",
                    "message", "Unexpected error"
            ));
            return model;
        }
    }
}