package com.cparedesr.dockia.agents.service.docker.impl;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;
import com.cparedesr.dockia.agents.service.docker.DockerService;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

public class DockerSocketCliServiceImplTest {

    private DockerSocketCliServiceImpl docker;

    @Before
    public void setUp() {
        docker = new DockerSocketCliServiceImpl();
        Properties props = new Properties();
        props.setProperty("alfresco.aiagents.docker.enabled", "false");
        props.setProperty("alfresco.aiagents.docker.mode", "socket");
        props.setProperty("alfresco.aiagents.docker.socket", "/var/run/docker.sock");
        docker.setGlobalProperties(props);
    }

    @Test
    public void whenDisabledReturnsDisabledState() {
        DockerService.CreateResult r = docker.createAndStart(
                "agent-1",
                "busybox:latest",
                Map.of("A", "B"),
                Map.of("l1", "v1"),
                null
        );

        assertNull(r.getContainerId());
        assertEquals("disabled", r.getCurrentState());
    }
}