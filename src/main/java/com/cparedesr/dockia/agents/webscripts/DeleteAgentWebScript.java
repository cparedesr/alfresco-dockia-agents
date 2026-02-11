package com.cparedesr.dockia.agents.webscripts;

import com.cparedesr.dockia.agents.service.AgentDeleteService;
import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import org.springframework.extensions.webscripts.*;

import java.util.HashMap;
import java.util.Map;

public class DeleteAgentWebScript extends DeclarativeWebScript {

    private AgentDeleteService deleteService;

    public void setDeleteService(AgentDeleteService deleteService) {
        this.deleteService = deleteService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {

        Map<String, Object> model = new HashMap<>();

        try {
            String id = req.getServiceMatch().getTemplateVars().get("id");

            deleteService.deleteAgent(id);

            model.put("agentId", id);
            status.setCode(Status.STATUS_OK);
            return model;

        } catch (BadRequestException e) {
            int http = "NOT_FOUND".equals(e.getCode()) ? Status.STATUS_NOT_FOUND : Status.STATUS_BAD_REQUEST;
            status.setCode(http);
            model.put("error", Map.of(
                    "statusCode", http,
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