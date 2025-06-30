package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequest;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/** Sanity tests to verify connection to PubChem and response format. */
@WebAppConfiguration
public class ChemicalImportApiControllerMVCIT extends API_MVC_TestBase {

  private User user;
  private String apiKey;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(user);
  }

  //  @RunIfSystemPropertyDefined("nightly")
  @Test
  public void testSearchByName() throws Exception {
    ChemicalSearchRequest body =
        new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "chemical/search", user, body))
            .andExpect(status().isOk())
            .andReturn();

    List<ChemicalImportSearchResult> expected =
        List.of(
            new ChemicalImportSearchResult(
                "Aspirin",
                "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l",
                "CC(=O)OC1=CC=CC=C1C(=O)O",
                "C9H8O4",
                "2244",
                "https://pubchem.ncbi.nlm.nih.gov/compound/2244",
                "50-78-2"));

    ObjectMapper objectMapper = new ObjectMapper();
    List<ChemicalImportSearchResult> actual =
        objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});

    assertEquals(expected, actual);
  }

  //  @RunIfSystemPropertyDefined("nightly")
  @Test
  public void testImportByCID() throws Exception {
    List<String> body = List.of("2244", "2246");
    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "chemical/import", user, body))
            .andExpect(status().isCreated())
            .andReturn();

    assertEquals("", result.getResponse().getContentAsString());
  }
}
