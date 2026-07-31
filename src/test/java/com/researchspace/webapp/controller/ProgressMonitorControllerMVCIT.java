package com.researchspace.webapp.controller;

import static com.researchspace.session.SessionAttributeUtils.BATCH_REGISTRATION_PROGRESS;
import static com.researchspace.session.SessionAttributeUtils.BATCH_WORDIMPORT_PROGRESS;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.testutils.TestFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

public class ProgressMonitorControllerMVCIT extends MVCTestBase {

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void progressReturnsNoopMonitorIfNoneSet() throws Exception {
    logoutAndLoginAs(piUser);
    MvcResult result =
        mockMvc
            .perform(get("/progress/{progressId}", BATCH_WORDIMPORT_PROGRESS))
            .andExpect(jsonPath("$.percentComplete", is(0.0)))
            .andExpect(jsonPath("$.done", is(false)))
            .andReturn();
    assertNull(result.getResolvedException());
  }

  @Test
  public void testProgressMessages() throws Exception {

    MvcResult progress1 =
        mockMvc
            .perform(
                get("/progress/{progressId}", SessionAttributeUtils.BATCH_WORDIMPORT_PROGRESS)
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description", Matchers.is("")))
            .andReturn();

    ProgressMonitor testmonitor = TestFactory.createAProgressMonitor(100, "uploading");
    testmonitor.worked(50);
    MvcResult progress2 =
        mockMvc
            .perform(
                get("/progress/{progressId}", BATCH_REGISTRATION_PROGRESS)
                    .sessionAttr(BATCH_REGISTRATION_PROGRESS, testmonitor)
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description", is("uploading")))
            .andExpect(jsonPath("$.percentComplete", is(50.0)))
            .andExpect(jsonPath("$.done", is(false)))
            .andReturn();

    testmonitor.done();
    mockMvc
        .perform(
            get("/progress/{progressId}", BATCH_REGISTRATION_PROGRESS)
                .sessionAttr(BATCH_REGISTRATION_PROGRESS, testmonitor)
                .principal(mockPrincipal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.done", is(true)));
  }
}
