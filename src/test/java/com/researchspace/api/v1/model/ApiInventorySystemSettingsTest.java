package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiInventorySystemSettingsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private IdentifierSettings settings(IdentifierType provider, String username, String password) {
    IdentifierSettings s = new IdentifierSettings();
    s.setProvider(provider);
    s.setServerUrl("https://example.org");
    s.setUsername(username);
    s.setPassword(password);
    s.setRepositoryPrefix("PFX");
    s.setEnabled("true");
    return s;
  }

  @Test
  void serializesEachSettingTypeAsAnArray() throws Exception {
    ApiInventorySystemSettings settings = new ApiInventorySystemSettings();
    settings.addSetting(
        InventorySettingType.IGSN, settings(IdentifierType.IGSN_DATACITE, "u", "p"));

    String json = mapper.writeValueAsString(settings);

    assertTrue(json.contains("\"identifiersSettings\""), json);
    assertTrue(json.contains("\"IGSN\":["), json); // value is now a JSON array
    assertTrue(json.contains("\"provider\":\"IGSN_DATACITE\""), json);
    assertFalse(json.contains("\"datacite\""), json);
  }

  @Test
  void deserializesArrayShapeWithMultipleProvidersPerType() throws Exception {
    String json =
        "{\"identifiersSettings\":{\"PIDINST\":["
            + "{\"provider\":\"PIDINST_DATACITE\",\"username\":\"u1\",\"password\":\"p1\"},"
            + "{\"provider\":\"PIDINST_B2INST\",\"username\":\"comm\",\"password\":\"tok\"}]}}";

    ApiInventorySystemSettings settings = mapper.readValue(json, ApiInventorySystemSettings.class);

    Set<IdentifierSettings> pidinst =
        settings.getIdentifiersSettings().get(InventorySettingType.PIDINST);
    assertNotNull(pidinst);
    assertEquals(2, pidinst.size());
    IdentifierSettings b2inst =
        settings.findByProvider(IdentifierType.PIDINST_B2INST).orElseThrow();
    assertEquals("comm", b2inst.getUsername());
    assertEquals("tok", b2inst.getPassword());
  }

  @Test
  void addSettingGroupsByTypeAndFindByProviderLocatesEntry() {
    ApiInventorySystemSettings settings = new ApiInventorySystemSettings();
    settings.addSetting(
        InventorySettingType.PIDINST, settings(IdentifierType.PIDINST_DATACITE, "u", "p"));
    settings.addSetting(
        InventorySettingType.PIDINST, settings(IdentifierType.PIDINST_B2INST, "comm", "tok"));

    assertEquals(2, settings.getIdentifiersSettings().get(InventorySettingType.PIDINST).size());
    assertEquals(
        IdentifierType.PIDINST_B2INST,
        settings.findByProvider(IdentifierType.PIDINST_B2INST).orElseThrow().getProvider());
    assertTrue(settings.findByProvider(IdentifierType.IGSN_DATACITE).isEmpty());
  }

  @Test
  void identifiersSettingsMapEmptyByDefault() {
    ApiInventorySystemSettings settings = new ApiInventorySystemSettings();
    assertNotNull(settings.getIdentifiersSettings());
    assertTrue(settings.getIdentifiersSettings().isEmpty());
  }
}
