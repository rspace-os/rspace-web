package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.researchspace.Constants;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.system.SystemPropertyTestFactory;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
public class PublicRecordsControllerMVCIT extends MVCTestBase {
  private @Autowired WebApplicationContext wac;
  private MockMvc mockMvc;
  private @Autowired RecordSharingManager recShareMgr;
  @Autowired private PublicRecordsController publicRecordsController;
  @Mock private SystemPropertyManager systemPropertyManagerMock;
  @Mock private SystemPropertyPermissionManager systemPropertyPermissionManagerMock;
  private SystemPropertyValue allowed =
      new SystemPropertyValue(SystemPropertyTestFactory.createASystemProperty(), "ALLOWED");
  private SystemPropertyValue denied =
      new SystemPropertyValue(SystemPropertyTestFactory.createASystemProperty(), "DENIED");

  @Before
  public void setUp() throws Exception {
    super.setUp();
    openMocks(this);
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    ReflectionTestUtils.setField(
        publicRecordsController, "systemPropertyManager", systemPropertyManagerMock);
    ReflectionTestUtils.setField(
        publicRecordsController,
        "systemPropertyPermissionManager",
        systemPropertyPermissionManagerMock);
  }

  @Test
  public void
      shouldListAllPublicDocumentsHavingPublishOnInternetTrueAndExcludeThosePublishedAsLinks()
          throws Exception {
    when(systemPropertyManagerMock.getAllSysadminPropertiesAsMap())
        .thenReturn(Collections.singletonMap("public_sharing", allowed));
    RecordGroupSharing publishOnInternetRgs = setUpGroupAndPublishRecordOnInternet(true);
    RecordGroupSharing publishAsLinkRgs = setUpGroupAndPublishRecordAsLink(true);
    MvcResult result = mockMvc.perform(get("/public/publishedView/publishedDocuments")).andReturn();
    assertEquals(
        "groups/sharing/public_published_records_list", result.getModelAndView().getViewName());
    List<RecordGroupSharing> sharedRecs =
        (List<RecordGroupSharing>) result.getModelAndView().getModel().get("sharedRecords");
    assertTrue(isRecordFound(publishOnInternetRgs, sharedRecs));
    assertFalse(isRecordFound(publishAsLinkRgs, sharedRecs));
  }

  public boolean isRecordFound(
      RecordGroupSharing target, List<RecordGroupSharing> sharedRecs, boolean dataHiding) {
    for (RecordGroupSharing published : sharedRecs) {
      if (target.getId().equals(published.getId())) {
        assertEquals(
            dataHiding ? "Publication disabled" : "publication_summary_text",
            published.getPublicationSummary());
        assertEquals(
            dataHiding ? "Publication disabled" : target.getShared().getName(),
            published.getShared().getName());
        return true;
      }
    }
    return false;
  }

  public boolean isRecordFound(RecordGroupSharing target, List<RecordGroupSharing> sharedRecs) {
    return isRecordFound(target, sharedRecs, false);
  }

  private RecordGroupSharing setUpGroupAndPublishRecordAsLink(boolean communityAdminAllows)
      throws Exception {
    GroupSetUp setupLinkOnly =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(
            "standardUser2", Constants.USER_ROLE, false, 1);
    publishDocumentForUser(setupLinkOnly.user, setupLinkOnly.structuredDocument.getId());
    when(systemPropertyPermissionManagerMock.isPropertyAllowed(
            eq(setupLinkOnly.user), eq("public_sharing")))
        .thenReturn(communityAdminAllows);
    RecordGroupSharing publishAsLinkRgs =
        recShareMgr.getSharedRecordsForUser(setupLinkOnly.user).get(0);
    return publishAsLinkRgs;
  }

  @NotNull
  private RecordGroupSharing setUpGroupAndPublishRecordOnInternet(boolean communityAdminAllows)
      throws Exception {
    GroupSetUp setupOnInternet =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(
            "standardUser1", Constants.USER_ROLE, false, 1);
    publishDocumentForUser(
        setupOnInternet.user,
        setupOnInternet.structuredDocument.getId(),
        true,
        true,
        "publication_summary_text");
    when(systemPropertyPermissionManagerMock.isPropertyAllowed(
            eq(setupOnInternet.user), eq("public_sharing")))
        .thenReturn(communityAdminAllows);
    RecordGroupSharing publishOnInternetRgs =
        recShareMgr.getSharedRecordsForUser(setupOnInternet.user).get(0);
    assertEquals("publication_summary_text", publishOnInternetRgs.getPublicationSummary());
    return publishOnInternetRgs;
  }

  @Test
  public void shouldHideAllDataWhenPublicSharingIsDisabledBySysAdmin() throws Exception {
    when(systemPropertyManagerMock.getAllSysadminPropertiesAsMap())
        .thenReturn(Collections.singletonMap("public_sharing", denied));
    RecordGroupSharing publishOnInternetRgs = setUpGroupAndPublishRecordOnInternet(true);
    MvcResult result = mockMvc.perform(get("/public/publishedView/publishedDocuments")).andReturn();
    assertEquals(
        "groups/sharing/public_published_records_list", result.getModelAndView().getViewName());
    List<RecordGroupSharing> sharedRecs =
        (List<RecordGroupSharing>) result.getModelAndView().getModel().get("sharedRecords");
    assertTrue(isRecordFound(publishOnInternetRgs, sharedRecs, true));
  }

  @Test
  public void shouldHideCommunityDataWhenPublicSharingIsDisabledByCommunityAdmin()
      throws Exception {
    when(systemPropertyManagerMock.getAllSysadminPropertiesAsMap())
        .thenReturn(Collections.singletonMap("public_sharing", allowed));
    RecordGroupSharing publishOnInternetRgs = setUpGroupAndPublishRecordOnInternet(false);
    MvcResult result = mockMvc.perform(get("/public/publishedView/publishedDocuments")).andReturn();
    assertEquals(
        "groups/sharing/public_published_records_list", result.getModelAndView().getViewName());
    List<RecordGroupSharing> sharedRecs =
        (List<RecordGroupSharing>) result.getModelAndView().getModel().get("sharedRecords");
    assertTrue(isRecordFound(publishOnInternetRgs, sharedRecs, true));
  }

  @Test
  public void shouldReturnPublishAllowed() throws Exception {
    when(systemPropertyManagerMock.getAllSysadminPropertiesAsMap())
        .thenReturn(Collections.singletonMap("public_sharing", allowed));
    MvcResult result =
        mockMvc.perform(get("/public/publishedView//ajax/publishedDocuments/allowed")).andReturn();
    Map json = parseJSONObjectFromResponseStream(result);
    assertTrue((Boolean) json.get("data"));
    when(systemPropertyManagerMock.getAllSysadminPropertiesAsMap())
        .thenReturn(Collections.singletonMap("public_sharing", denied));
    result =
        mockMvc.perform(get("/public/publishedView//ajax/publishedDocuments/allowed")).andReturn();
    json = parseJSONObjectFromResponseStream(result);
    assertFalse((Boolean) json.get("data"));
  }
}
