package com.researchspace.webapp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
public class GlobalLookupControllerMVCIT extends MVCTestBase {

  @Autowired private WebApplicationContext wac;

  private MockMvc mockMvc;

  @Before
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    super.setUp();
  }

  @Test
  public void testGetRedirects() throws Exception {
    GlobalIdentifier sdGid = new GlobalIdentifier(GlobalIdPrefix.SD, 12345L);
    assertRedirect(sdGid, StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL);

    GlobalIdentifier sdVersionGid = new GlobalIdentifier(GlobalIdPrefix.SD, 12345L, 2L);
    assertRedirect(sdVersionGid, StructuredDocumentController.STRUCTURED_DOCUMENT_AUDIT_VIEW_URL);

    GlobalIdentifier nbGid = new GlobalIdentifier(GlobalIdPrefix.NB, 12345L);
    assertRedirect(nbGid, NotebookEditorController.ROOT_URL);

    // notebook versions not supported, attempt to use versioned global id should throw exception
    GlobalIdentifier nbVersionGid = new GlobalIdentifier(GlobalIdPrefix.NB, 12345L, 23L);
    try {
      assertRedirect(nbVersionGid, NotebookEditorController.ROOT_URL);
      fail("expected redirect error on global identifier: " + nbVersionGid);
    } catch (AssertionError ae) {
      assertTrue(
          ae.getMessage().contains("but: was \"error\""),
          "expected redirect to 'error', but msg was: " + ae.getMessage());
    }

    GlobalIdentifier flGid = new GlobalIdentifier(GlobalIdPrefix.FL, 12345L);
    assertRedirect(flGid, WorkspaceController.ROOT_URL);

    GlobalIdentifier fmGid = new GlobalIdentifier(GlobalIdPrefix.FM, 12345L);
    assertRedirect(fmGid, GlobalLookupController.FORM_REDIRECT_URL);

    GlobalIdentifier glGid = new GlobalIdentifier(GlobalIdPrefix.GL, 12345L);
    assertRedirect(glGid, FileDownloadController.STREAM_URL);

    GlobalIdentifier gfGid = new GlobalIdentifier(GlobalIdPrefix.GF, 12345L);
    assertRedirect(gfGid, GalleryController.GALLERY_URL);

    GlobalIdentifier saGid = new GlobalIdentifier(GlobalIdPrefix.SA, 12345L);
    assertRedirect(saGid, "/inventory/sample");

    GlobalIdentifier ssGid = new GlobalIdentifier(GlobalIdPrefix.SS, 12345L);
    assertRedirect(ssGid, "/inventory/subsample");

    GlobalIdentifier sampleTemplate = new GlobalIdentifier(GlobalIdPrefix.IT, 12345L);
    assertRedirect(sampleTemplate, "/inventory/sampletemplate");

    GlobalIdentifier conGid = new GlobalIdentifier(GlobalIdPrefix.IC, 12345L);
    assertRedirect(conGid, "/inventory/container");

    GlobalIdentifier groupId = new GlobalIdentifier(GlobalIdPrefix.GP, 12345L);
    assertRedirect(groupId, "/groups/view");

    GlobalIdentifier userId = new GlobalIdentifier(GlobalIdPrefix.US, 12345L);
    assertRedirect(userId, "/userform?userId=12345");

    // here, we don't want to link to metadata about the bench itself, but see its contents.
    // hence, not using /workbenches/{id} links
    // rspac-2447
    GlobalIdentifier benchId = new GlobalIdentifier(GlobalIdPrefix.BE, 12345L);
    assertRedirect(benchId, "/inventory/search?parentGlobalId=BE12345");
  }

  private void assertRedirect(GlobalIdentifier gid, String expectedURL) throws Exception {
    this.mockMvc
        .perform(get("/globalId/{oid}", gid.getIdString()))
        .andExpect(view().name(containsString(expectedURL)))
        .andExpect(status().is3xxRedirection())
        .andReturn();
  }
}
