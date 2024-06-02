package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import java.security.Principal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class SVGMathControllerMVCIT extends MVCTestBase {

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void saveAndRetrieve() throws Exception {
    User any = createAndSaveUser(getRandomAlphabeticString("u1"));
    initUser(any);
    logoutAndLoginAs(any);
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(any, "any");
    Long fieldId = sdoc.getFields().get(0).getId();
    RSMath transientMath = TestFactory.createAMathElement();
    Principal subject = new MockPrincipal(any);
    MvcResult result =
        this.mockMvc
            .perform(postOKMath(subject, fieldId, transientMath))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    String resp = result.getResponse().getContentAsString();
    Long mathId = Long.parseLong(resp);
    assertNotNull(mathId);
    // success
    MvcResult getResult =
        this.mockMvc
            .perform(getMathSvg(subject, mathId))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    final int EXPECTED_BYTELENGTH = transientMath.getMathSvg().getData().length;
    assertEquals(EXPECTED_BYTELENGTH, getResult.getResponse().getContentAsByteArray().length);

    // unknown ID
    final long NOT_EXISTS_ID = -123L;
    MvcResult notFound =
        this.mockMvc
            .perform(getMathSvg(subject, NOT_EXISTS_ID))
            .andExpect(status().is4xxClientError())
            .andReturn();
  }

  private MockHttpServletRequestBuilder getMathSvg(Principal subject, Long mathId) {
    return MockMvcRequestBuilders.get("/svg/{id}", mathId + "").principal(subject);
  }

  private MockHttpServletRequestBuilder postOKMath(
      Principal subject, Long fieldId, RSMath transientMath) {
    return MockMvcRequestBuilders.post("/svg")
        .param("svg", transientMath.getMathSvgString())
        .param("latex", transientMath.getLatex())
        .param("fieldId", fieldId + "")
        .principal(subject);
  }
}
