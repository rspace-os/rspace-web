package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import org.junit.jupiter.api.Test;

class ApiInventorySystemSettingsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializesIdentifiersSettingsMap() throws Exception {
    ApiInventorySystemSettings settings = new ApiInventorySystemSettings();
    IdentifierSettings igsnSettings = settings.getOrCreate(InventorySettingType.IGSN);
    igsnSettings.setProvider(IdentifierType.DATACITE_IGSN);
    igsnSettings.setServerUrl("https://api.test.datacite.org");
    igsnSettings.setUsername("testuser");
    igsnSettings.setPassword("testpassword");
    igsnSettings.setRepositoryPrefix("TESTPREFIX");
    igsnSettings.setEnabled("true");

    String json = mapper.writeValueAsString(settings);

    assertTrue(json.contains("\"identifiersSettings\""), json);
    assertTrue(json.contains("\"IGSN\""), json);
    assertTrue(json.contains("\"provider\":\"DATACITE_IGSN\""), json);
    assertTrue(json.contains("\"serverUrl\":\"https://api.test.datacite.org\""), json);
    assertTrue(json.contains("\"username\":\"testuser\""), json);
    assertTrue(json.contains("\"password\":\"testpassword\""), json);
    assertTrue(json.contains("\"repositoryPrefix\":\"TESTPREFIX\""), json);
    assertTrue(json.contains("\"enabled\":\"true\""), json);
    assertFalse(json.contains("\"datacite\""), json);
  }

  @Test
  void deserializesIdentifiersSettingsMap() throws Exception {
    String json =
        "{\"identifiersSettings\": {"
            + "\"IGSN\": {\"provider\": \"DATACITE_IGSN\", \"serverUrl\":"
            + " \"https://api.test.datacite.org\", \"username\": \"testuser\", \"password\":"
            + " \"testpassword\", \"repositoryPrefix\": \"TESTPREFIX\", \"enabled\": \"true\"}}}";

    ApiInventorySystemSettings settings = mapper.readValue(json, ApiInventorySystemSettings.class);

    IdentifierSettings igsnSettings =
        settings.getIdentifiersSettings().get(InventorySettingType.IGSN);
    assertNotNull(igsnSettings);
    assertEquals(IdentifierType.DATACITE_IGSN, igsnSettings.getProvider());
    assertEquals("https://api.test.datacite.org", igsnSettings.getServerUrl());
    assertEquals("testuser", igsnSettings.getUsername());
    assertEquals("testpassword", igsnSettings.getPassword());
    assertEquals("TESTPREFIX", igsnSettings.getRepositoryPrefix());
    assertEquals("true", igsnSettings.getEnabled());
  }

  @Test
  void deserializesFullTicketExampleWithBothEntries() throws Exception {
    String json =
        "{\"identifiersSettings\": {"
            + "\"IGSN\": {\"provider\": \"DATACITE_IGSN\", \"serverUrl\":"
            + " \"https://api.test.datacite.org\", \"username\": \"testuser\", \"password\":"
            + " \"testpassword\", \"repositoryPrefix\": \"TESTPREFIX\", \"enabled\": \"true\"},"
            + "\"PDINST\": {\"provider\": \"DATACITE_PDINST\", \"serverUrl\":"
            + " \"https://api.test.datacite.org\", \"username\": \"testuser2\", \"password\":"
            + " \"testpassword2\", \"repositoryPrefix\": \"TESTPREFIX2\", \"enabled\": \"true\"}}}";

    ApiInventorySystemSettings settings = mapper.readValue(json, ApiInventorySystemSettings.class);

    IdentifierSettings pdinstSettings =
        settings.getIdentifiersSettings().get(InventorySettingType.PDINST);
    assertNotNull(pdinstSettings);
    assertEquals(IdentifierType.DATACITE_PDINST, pdinstSettings.getProvider());
    assertEquals("testuser2", pdinstSettings.getUsername());
    assertEquals("testpassword2", pdinstSettings.getPassword());
    assertEquals("TESTPREFIX2", pdinstSettings.getRepositoryPrefix());
    assertEquals("true", pdinstSettings.getEnabled());

    // round-trips, including the B2INST_PDINST provider value
    pdinstSettings.setProvider(IdentifierType.B2INST_PDINST);
    String reserialized = mapper.writeValueAsString(settings);
    assertTrue(reserialized.contains("\"provider\":\"B2INST_PDINST\""), reserialized);
  }

  @Test
  void identifiersSettingsMapEmptyByDefault() {
    ApiInventorySystemSettings settings = new ApiInventorySystemSettings();
    assertNotNull(settings.getIdentifiersSettings());
    assertTrue(settings.getIdentifiersSettings().isEmpty());
  }

  @Test
  void getOrCreateInitializesMissingEntry() {
    ApiInventorySystemSettings settings = new ApiInventorySystemSettings();
    IdentifierSettings pdinstSettings = settings.getOrCreate(InventorySettingType.PDINST);
    assertNotNull(pdinstSettings);
    assertEquals(
        pdinstSettings, settings.getIdentifiersSettings().get(InventorySettingType.PDINST));
  }
}
