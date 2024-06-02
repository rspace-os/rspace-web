package com.researchspace.webapp.controller;

import static org.junit.Assert.assertNotEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO.ArchiveDialogConfig;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.model.repository.RepoDepositMeta;
import com.researchspace.model.repository.RepositoryTestFactory;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class ArchiveDepositMVCIT extends MVCTestBase {

  ObjectMapper jsonWriter;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    jsonWriter = new ObjectMapper();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void validateRepoConfig() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(user);
    logoutAndLoginAs(user);

    ExportSelection exportSelection = new ExportSelection();
    exportSelection.setExportIds(ids(2));
    exportSelection.setExportTypes(types(ExportController.maxIdsToProcess));
    exportSelection.setExportNames(names(ExportController.maxIdsToProcess));

    ArchiveDialogConfig archiveDialogConfig = new ArchiveDialogConfig();
    archiveDialogConfig.setArchiveType("html");
    archiveDialogConfig.setMaxLinkLevel(3);
    archiveDialogConfig.setDescription("abc");

    RepoDepositMeta meta = RepositoryTestFactory.createAValidRepoDepositMeta();
    RepoDepositConfig repoCfg = new RepoDepositConfig();
    repoCfg.setMeta(meta);
    meta.setDescription("xxxxxxxxxxxx");
    Long appSetId = 1L;
    repoCfg.setRepoCfg(appSetId);
    repoCfg.setDepositToRepository(true);

    ExportArchiveDialogConfigDTO exportArchiveConfig = new ExportArchiveDialogConfigDTO();
    exportArchiveConfig.setExportSelection(exportSelection);
    exportArchiveConfig.setExportConfig(archiveDialogConfig);
    exportArchiveConfig.setRepositoryConfig(repoCfg);

    // empty title will return error
    String json = jsonWriter.writeValueAsString(exportArchiveConfig);
    MvcResult result =
        mockMvc
            .perform(
                post("/export/ajax/exportArchive")
                    .content(json)
                    .contentType(APPLICATION_JSON)
                    .principal(user::getUsername))
            .andReturn();
    assertNotEquals(
        ExportController.SUBMITTED_SUCCESS_MSG, result.getResponse().getContentAsString());
  }

  String[] names(int size) {
    String[] tooManyNames = new String[size];
    Arrays.fill(tooManyNames, "name");
    return tooManyNames;
  }

  String[] types(int size) {
    String[] tooManyTypes = new String[size];
    Arrays.fill(tooManyTypes, RecordType.NORMAL.name());
    return tooManyTypes;
  }

  Long[] ids(int size) {
    Long[] tooMany = new Long[size];
    Arrays.fill(tooMany, 1L);
    return tooMany;
  }
}
