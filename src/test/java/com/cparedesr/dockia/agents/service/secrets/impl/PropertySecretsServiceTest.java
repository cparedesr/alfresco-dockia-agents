package com.cparedesr.dockia.agents.service.secrets.impl;

import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

public class PropertySecretsServiceTest {

    private PropertySecretsService service;

    @Before
    public void setUp() {
        service = new PropertySecretsService();
        Properties props = new Properties();
        props.setProperty("alfresco.aiagents.secret.svc_ai_password", "supersecret");
        service.setGlobalProperties(props);
    }

    @Test
    public void resolvesPropSecret() {
        String value = service.resolve("prop:alfresco.aiagents.secret.svc_ai_password");
        assertEquals("supersecret", value);
    }

    @Test(expected = BadRequestException.class)
    public void throwsWhenSecretRefEmpty() {
        service.resolve("");
    }

    @Test(expected = BadRequestException.class)
    public void throwsWhenKeyMissing() {
        service.resolve("prop:missing.key");
    }

    @Test(expected = BadRequestException.class)
    public void throwsWhenUnsupportedScheme() {
        service.resolve("vault:kv/some#key");
    }
}