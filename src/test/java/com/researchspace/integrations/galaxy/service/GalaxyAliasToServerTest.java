package com.researchspace.integrations.galaxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** RSDEV-1525: the server token is read from deployment.properties, never sent to the browser. */
class GalaxyAliasToServerTest {

  @Test
  void serverTokenIsReadButNeverWritten() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    GalaxyAliasToServer server =
        mapper.readValue(
            "{\"alias\":\"eu\",\"url\":\"https://usegalaxy.eu\",\"token\":\"galaxy-secret\"}",
            GalaxyAliasToServer.class);

    assertEquals("galaxy-secret", server.getToken());
    assertThat(mapper.writeValueAsString(server)).doesNotContain("galaxy-secret");
  }
}
