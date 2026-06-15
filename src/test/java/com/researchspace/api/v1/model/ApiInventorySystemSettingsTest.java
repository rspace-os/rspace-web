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
    igsnSettings.setProvider(IdentifierType.IGSN_DATACITE);
    igsnSettings.setServerUrl("https://api.test.datacite.org");
    igsnSettings.setUsername("testuser");
    igsnSettings.setPassword("testpassword");
    igsnSettings.setRepositoryPrefix("TESTPREFIX");
    igsnSettings.setEnabled("true");

    String json = mapper.writeValueAsString(settings);

    assertTrue(json.contains("\"identifiersSettings\""), json);
    assertTrue(json.contains("\"IGSN\""), json);
    assertTrue(json.contains("\"provider\":\"IGSN_DATACITE\""), json);
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
            + "\"IGSN\": {\"provider\": \"IGSN_DATACITE\", \"serverUrl\":"
            + " \"https://api.test.datacite.org\", \"username\": \"testuser\", \"password\":"
            + " \"testpassword\", \"repositoryPrefix\": \"TESTPREFIX\", \"enabled\": \"true\"}}}";

    ApiInventorySystemSettings settings = mapper.readValue(json, ApiInventorySystemSettings.class);

    IdentifierSettings igsnSettings =
        settings.getIdentifiersSettings().get(InventorySettingType.IGSN);
    assertNotNull(igsnSettings);
    assertEquals(IdentifierType.IGSN_DATACITE, igsnSettings.getProvider());
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
            + "\"IGSN\": {\"provider\": \"IGSN_DATACITE\", \"serverUrl\":"
            + " \"https://api.test.datacite.org\", \"username\": \"testuser\", \"password\":"
            + " \"testpassword\", \"repositoryPrefix\": \"TESTPREFIX\", \"enabled\": \"true\"},"
            + "\"PDINST\": {\"provider\": \"PDINST_DATACITE\", \"serverUrl\":"
            + " \"https://api.test.datacite.org\", \"username\": \"testuser2\", \"password\":"
            + " \"testpassword2\", \"repositoryPrefix\": \"TESTPREFIX2\", \"enabled\": \"true\"}}}";

    ApiInventorySystemSettings settings = mapper.readValue(json, ApiInventorySystemSettings.class);

    IdentifierSettings pdinstSettings =
        settings.getIdentifiersSettings().get(InventorySettingType.PDINST);
    assertNotNull(pdinstSettings);
    assertEquals(IdentifierType.PDINST_DATACITE, pdinstSettings.getProvider());
    assertEquals("testuser2", pdinstSettings.getUsername());
    assertEquals("testpassword2", pdinstSettings.getPassword());
    assertEquals("TESTPREFIX2", pdinstSettings.getRepositoryPrefix());
    assertEquals("true", pdinstSettings.getEnabled());

    // round-trips, including the PDINST_B2INST provider value
    pdinstSettings.setProvider(IdentifierType.PDINST_B2INST);
    String reserialized = mapper.writeValueAsString(settings);
    assertTrue(reserialized.contains("\"provider\":\"PDINST_B2INST\""), reserialized);
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
