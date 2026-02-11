package com.cparedesr.dockia.agents.service.docker.impl;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;
import com.cparedesr.dockia.agents.service.docker.DockerService;
import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import org.alfresco.util.Pair;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.KeyManagerFactory;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
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
            throw new BadRequestException("DOCKER_MODE_UNSUPPORTED", "Only alfresco.aiagents.docker.mode=socket is supported for create/start");
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

    @Override
    public void remove(String containerId, boolean force) {
        boolean enabled = Boolean.parseBoolean(globalProperties.getProperty("alfresco.aiagents.docker.enabled", "true"));
        if (!enabled) return;

        if (containerId == null || containerId.trim().isEmpty()) return;

        String mode = globalProperties.getProperty("alfresco.aiagents.docker.mode", "socket");

        if ("socket".equalsIgnoreCase(mode)) {
            removeBySocket(containerId.trim(), force);
            return;
        }

        if ("url".equalsIgnoreCase(mode)) {
            removeByRemoteApi(containerId.trim(), force);
            return;
        }

        throw new BadRequestException("DOCKER_MODE_UNSUPPORTED", "Unsupported alfresco.aiagents.docker.mode: " + mode);
    }

    // ---------------- socket mode ----------------

    private void removeBySocket(String containerId, boolean force) {
        String socket = globalProperties.getProperty("alfresco.aiagents.docker.socket", "/var/run/docker.sock");

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("--host");
        cmd.add("unix://" + socket);
        cmd.add("rm");
        if (force) cmd.add("-f");
        cmd.add(containerId);

        // Si el contenedor no existe, hacemos DELETE idempotente (no fallar)
        try {
            execOrFail(cmd);
        } catch (BadRequestException e) {
            String msg = (e.getMessage() == null) ? "" : e.getMessage().toLowerCase();
            if (msg.contains("no such container") || msg.contains("not found")) {
                return; // idempotente
            }
            throw e;
        }
    }

    // ---------------- url mode (Docker Remote API TLS) ----------------

    private void removeByRemoteApi(String containerId, boolean force) {
        String baseUrl = globalProperties.getProperty("alfresco.aiagents.docker.baseUrl", "").trim();
        if (baseUrl.isEmpty()) {
            throw new BadRequestException("DOCKER_BASEURL_REQUIRED", "alfresco.aiagents.docker.baseUrl is required for mode=url");
        }

        // Docker Engine API: DELETE /containers/{id}?force=true
        String url = baseUrl.replaceAll("/+$", "") + "/containers/" + containerId + "?force=" + (force ? "true" : "false");

        try {
            HttpClient client = buildTlsHttpClientIfConfigured();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .DELETE()
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

            // 204 No Content => OK
            if (res.statusCode() == 204) return;

            // 404 Not Found => idempotente
            if (res.statusCode() == 404) return;

            throw new BadRequestException("DOCKER_REMOTE_API_ERROR",
                    "Docker API error " + res.statusCode() + ": " + safeBody(res.body()));

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("DOCKER_REMOTE_API_EXEC_FAILED", "Failed to call Docker Remote API");
        }
    }

    /**
     * Construye HttpClient con TLS mutual si keystore/truststore están configurados.
     * Si no, usa client por defecto.
     */
    private HttpClient buildTlsHttpClientIfConfigured() {
        String ksPath = globalProperties.getProperty("alfresco.aiagents.docker.tls.keystore.path", "").trim();
        String ksPass = globalProperties.getProperty("alfresco.aiagents.docker.tls.keystore.password", "").trim();
        String tsPath = globalProperties.getProperty("alfresco.aiagents.docker.tls.truststore.path", "").trim();
        String tsPass = globalProperties.getProperty("alfresco.aiagents.docker.tls.truststore.password", "").trim();

        boolean hasTls = !ksPath.isEmpty() && !tsPath.isEmpty();

        if (!hasTls) {
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }

        try {
            SSLContext sslContext = buildSslContext(ksPath, ksPass, tsPath, tsPass);

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

        } catch (Exception e) {
            throw new BadRequestException("DOCKER_TLS_CONFIG_ERROR", "Invalid Docker TLS configuration");
        }
    }

    private SSLContext buildSslContext(String keystorePath, String keystorePass,
                                       String truststorePath, String truststorePass) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(keystorePath)) {
            keyStore.load(in, keystorePass.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePass.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(truststorePath)) {
            trustStore.load(in, truststorePass.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    private String safeBody(String body) {
        if (body == null) return "";
        String b = body.trim();
        return b.length() > 500 ? b.substring(0, 500) + "..." : b;
    }

    // ---------------- CLI helpers (los tuyos) ----------------

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