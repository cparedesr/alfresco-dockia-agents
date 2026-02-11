package com.cparedesr.dockia.agents.service.docker.impl;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;
import com.cparedesr.dockia.agents.service.docker.DockerService;
import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import org.alfresco.util.Pair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DockerSocketCliServiceImpl implements DockerService {

    private Properties globalProperties;

    public void setGlobalProperties(Properties globalProperties) {
        this.globalProperties = globalProperties;
    }

    @Override
    public CreateResult createAndStart(String agentId, String image, Map<String, String> env, Map<String, String> labels,
                                      List<AgentDeployRequest.PortMapping> ports) {

        boolean enabled = Boolean.parseBoolean(globalProperties.getProperty("alfresco.aiagents.docker.enabled", "true"));
        if (!enabled) {
            return new CreateResult(null, "disabled");
        }

        String mode = globalProperties.getProperty("alfresco.aiagents.docker.mode", "socket");
        if (!"socket".equalsIgnoreCase(mode)) {
            throw new BadRequestException("DOCKER_MODE_UNSUPPORTED", "Only alfresco.aiagents.docker.mode=socket is supported");
        }

        String socket = globalProperties.getProperty("alfresco.aiagents.docker.socket", "/var/run/docker.sock");

        // docker create --name agentId ... image
        List<String> cmdCreate = new ArrayList<>();
        cmdCreate.add("docker");
        cmdCreate.add("--host");
        cmdCreate.add("unix://" + socket);
        cmdCreate.add("create");
        cmdCreate.add("--name");
        cmdCreate.add(agentId);

        for (var e : labels.entrySet()) {
            cmdCreate.add("--label");
            cmdCreate.add(e.getKey() + "=" + e.getValue());
        }

        for (var e : env.entrySet()) {
            cmdCreate.add("-e");
            cmdCreate.add(e.getKey() + "=" + e.getValue());
        }

        if (ports != null) {
            for (AgentDeployRequest.PortMapping p : ports) {
                String proto = p.getProtocol() == null ? "tcp" : p.getProtocol();
                cmdCreate.add("-p");
                cmdCreate.add(p.getHostPort() + ":" + p.getContainerPort() + "/" + proto);
            }
        }

        cmdCreate.add(image);

        String containerId = execAndGetFirstLine(cmdCreate);
        if (containerId.isBlank()) throw new BadRequestException("DOCKER_CREATE_FAILED", "docker create did not return container id");

        // docker start <id>
        List<String> cmdStart = Arrays.asList(
                "docker", "--host", "unix://" + socket,
                "start", containerId
        );
        execOrFail(cmdStart);

        return new CreateResult(containerId, "running");
    }

    private String execAndGetFirstLine(List<String> cmd) {
        Pair<Integer, String> r = exec(cmd);
        if (r.getFirst() != 0) {
            throw new BadRequestException("DOCKER_CLI_ERROR", r.getSecond());
        }
        for (String line : r.getSecond().split("\n")) {
            String s = line.trim();
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    private void execOrFail(List<String> cmd) {
        Pair<Integer, String> r = exec(cmd);
        if (r.getFirst() != 0) {
            throw new BadRequestException("DOCKER_CLI_ERROR", r.getSecond());
        }
    }

    private Pair<Integer, String> exec(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }

            int exit = p.waitFor();
            return new Pair<>(exit, out.toString());

        } catch (Exception e) {
            throw new BadRequestException("DOCKER_CLI_EXEC_FAILED", "Failed to execute docker CLI");
        }
    }
}