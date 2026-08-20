package com.researchspace.model.dto;

import com.researchspace.model.apps.App;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationInfoTest {
    IntegrationInfo integrationInfo;

    @BeforeEach
    void before() {
        integrationInfo = new IntegrationInfo();
    }

    @Test
    void isOAuthAppUsable() {
        assertFalse(integrationInfo.isOAuthAppUsable());
        integrationInfo.setAvailable(true);
        assertFalse(integrationInfo.isOAuthAppUsable());
        integrationInfo.setEnabled(true);
        assertFalse(integrationInfo.isOAuthAppUsable());
        integrationInfo.setOauthConnected(true);
        assertTrue(integrationInfo.isOAuthAppUsable());
    }

    @Test
    void appToInfoNameRoundTrip() {
        String FIGSHARE_INFO_NAME="FIGSHARE";
        String figshare = IntegrationInfo.getAppNameFromIntegrationName(FIGSHARE_INFO_NAME);
        App app = new App(figshare, "fs", true);
        assertEquals(FIGSHARE_INFO_NAME, app.toIntegrationInfoName());
    }
}