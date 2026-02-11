package com.cparedesr.dockia.agents.webscripts;

import com.cparedesr.dockia.agents.model.AgentDetail;
import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import com.cparedesr.dockia.agents.service.registry.AgentRegistryService;
import org.springframework.extensions.webscripts.*;

import java.util.HashMap;
import java.util.Map;

public class GetAgentByIdWebScript extends DeclarativeWebScript {

    private AgentRegistryService registryService;

    public void setRegistryService(AgentRegistryService registryService) {
        this.registryService = registryService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {

        Map<String, Object> model = new HashMap<>();

        try {
            String id = getPathVar(req, "id");
            AgentDetail detail = registryService.getAgentDetailByAgentId(id);

            model.put("data", detail);

            String base = req.getServiceContextPath() + "/api/-default-/public/ai-agents/versions/1/agents/" + id;
            model.put("links", Map.of(
                    "self", base,
                    "status", base + "/status"
            ));

            status.setCode(Status.STATUS_OK);
            return model;

        } catch (BadRequestException e) {
            int code = "NOT_FOUND".equals(e.getCode()) ? Status.STATUS_NOT_FOUND : Status.STATUS_BAD_REQUEST;
            status.setCode(code);
            model.put("error", Map.of(
                    "statusCode", code,
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

    private String getPathVar(WebScriptRequest req, String name) {
        Map<String, String> vars = req.getServiceMatch().getTemplateVars();
        String v = vars.get(name);
        return v == null ? null : v.trim();
    }
}