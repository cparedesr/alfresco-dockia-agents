package com.cparedesr.dockia.agents.webscripts;

import com.cparedesr.dockia.agents.model.AgentSummary;
import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import com.cparedesr.dockia.agents.service.registry.AgentRegistryService;
import org.springframework.extensions.webscripts.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListAgentsGetWebScript extends DeclarativeWebScript {

    private AgentRegistryService registryService;

    public void setRegistryService(AgentRegistryService registryService) {
        this.registryService = registryService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {

        Map<String, Object> model = new HashMap<>();

        try {
            int skip = intParam(req, "skipCount", 0);
            int max = intParam(req, "maxItems", 100);

            List<AgentSummary> items = registryService.listAgents(skip, max);

            Map<String, Object> data = new HashMap<>();
            data.put("count", items.size());
            data.put("items", items);

            model.put("data", data);
            status.setCode(Status.STATUS_OK);
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

    private int intParam(WebScriptRequest req, String name, int def) {
        String v = req.getParameter(name);
        if (v == null || v.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}