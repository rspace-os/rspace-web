package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import org.junit.jupiter.api.Test;

class ApiInventoryLinkTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializesRelationTypeAndTarget() throws Exception {
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType("IsCalibratedBy");
    link.setTargetGlobalId("SA123");

    String json = mapper.writeValueAsString(link);

    assertEquals(
        "{\"relationType\":\"IsCalibratedBy\",\"targetGlobalId\":\"SA123\",\"versionPin\":null}",
        json);
  }

  @Test
  void versionPinDerivedFromGlobalIdSuffix() {
    ApiInventoryLink link = new ApiInventoryLink();
    link.setTargetGlobalId("SA123v3");

    assertEquals(Long.valueOf(3), link.derivedVersionPin());
  }

  @Test
  void noVersionPinWhenGlobalIdHasNoSuffix() {
    ApiInventoryLink link = new ApiInventoryLink();
    link.setTargetGlobalId("SA123");

    assertNull(link.derivedVersionPin());
  }

  @Test
  void roundTripJsonPreservesValues() throws Exception {
    String json =
        "{\"relationType\":\"References\",\"targetGlobalId\":\"SS42v7\",\"versionPin\":7}";
    ApiInventoryLink parsed = mapper.readValue(json, ApiInventoryLink.class);
    assertEquals("References", parsed.getRelationType());
    assertEquals("SS42v7", parsed.getTargetGlobalId());
    assertEquals(Long.valueOf(7), parsed.getVersionPin());
    assertNotNull(parsed);
  }

  @Test
  void serializesAFilestoreTargetAfterTheGlobalIdFields() throws Exception {
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType("IsSourceOf");
    link.setFilestoreTarget(new ApiInventoryLinkFilestoreTarget(12L, "/XRD/run.dat"));

    assertEquals(
        "{\"relationType\":\"IsSourceOf\",\"targetGlobalId\":null,\"versionPin\":null,"
            + "\"filestoreTarget\":{\"filestoreId\":12,\"remotePath\":\"/XRD/run.dat\"}}",
        mapper.writeValueAsString(link));
  }

  @Test
  void readsAFilestoreTargetFromJson() throws Exception {
    ApiInventoryLink parsed =
        mapper.readValue(
            "{\"relationType\":\"IsSourceOf\","
                + "\"filestoreTarget\":{\"filestoreId\":12,\"remotePath\":\"/XRD\"}}",
            ApiInventoryLink.class);

    assertTrue(parsed.hasFilestoreTarget());
    assertTrue(parsed.hasTarget());
    assertEquals(12L, parsed.getFilestoreTarget().getFilestoreId());
    assertEquals("12:/XRD", parsed.getFilestoreTarget().asLogicPath());
    assertNull(parsed.getTargetGlobalId());
  }

  @Test
  void builtFromAFilestoreLinkItCarriesTheFilestoreAndTheStoredPath() {
    InventoryLink entity = new InventoryLink();
    entity.setRelationType("IsSourceOf");
    entity.setFilestoreTarget(
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(12L, "My S3", new User("o")),
        "XRD/run.dat");

    ApiInventoryLink api = new ApiInventoryLink(entity);

    assertEquals(12L, api.getFilestoreTarget().getFilestoreId());
    assertEquals("/XRD/run.dat", api.getFilestoreTarget().getRemotePath());
    assertNull(api.getTargetGlobalId());
    assertNull(api.getVersionPin());
  }
}
