package com.researchspace.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Inventory structure search against the real chemistry service, the same service the Workspace
 * chemical search uses. Needs {@code chemistry.service.url}; runs in the chemistry test group.
 */
@Tag("chemistry")
@TestPropertySource(properties = "chemistry.provider=indigo")
public class InventoryStructureSearchChemistryMVCIT extends API_MVC_InventoryTestBase {

  private static final String BENZENE = "c1ccccc1";

  private @Autowired SystemPropertyManager systemPropertyManager;

  private User user;
  private String apiKey;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(user);
    systemPropertyManager.save(
        SystemPropertyName.CHEMISTRY_AVAILABLE, HierarchicalPermission.ALLOWED, null);
    userMgr.setPreference(Preference.CHEMISTRY, "true", user.getUsername());
  }

  private ApiInventorySearchResult structureSearch(String smiles, String searchType)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", user)
                    .param("chemQuery", smiles)
                    .param("chemSearchType", searchType))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiInventorySearchResult.class);
  }

  @Test
  void findsSampleCarryingMatchingGalleryChemistryFile() throws Exception {
    ApiSampleWithFullSubSamples withChemistry = createBasicSampleForUser(user, "with chemistry");
    createBasicSampleForUser(user, "without chemistry");
    EcatChemistryFile aminoglutethimide = addChemistryFileToGallery("Aminoglutethimide.mol", user);
    inventoryFileApiMgr.attachGalleryFileToInventoryRecord(
        new GlobalIdentifier(withChemistry.getGlobalId()), aminoglutethimide.getOid(), user);

    // benzene is a substructure of aminoglutethimide: the sample is the hit, not its subsample
    ApiInventorySearchResult substructure = structureSearch(BENZENE, "SUBSTRUCTURE");
    assertThat(substructure.getRecords())
        .extracting(ApiInventoryRecordInfo::getGlobalId)
        .containsExactly(withChemistry.getGlobalId());
    assertThat(substructure.getLinks().get(0).getLink()).contains("chemQuery=");

    // but not an exact match
    assertThat(structureSearch(BENZENE, "EXACT").getRecords()).isEmpty();
  }
}
