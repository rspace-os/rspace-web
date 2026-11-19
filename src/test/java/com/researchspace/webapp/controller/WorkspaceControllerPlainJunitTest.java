package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.dtos.WorkspaceSettings;
import com.researchspace.model.dtos.WorkspaceSettings.WorkspaceViewMode;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

@ExtendWith(MockitoExtension.class)
public class WorkspaceControllerPlainJunitTest {
  private static final int TEMPLATE_COUNT = 5;

  @Mock RecordManager recordMgr;
  @Mock UserManager userMgr;
  @Mock RecordSharingManager sharingMgr;
  @Mock IControllerInputValidator inputValidator;
  @Mock IPropertyHolder properties;
  @Mock WorkspaceViewModePreferences workspaceViewModePreferences;
  @Mock PaginationSettingsPreferences paginationSettingsPreferences;

  @InjectMocks WorkspaceController workspaceCtrller;
  private static final String SETTINGS_KEY = "abc123";
  User anyUser;

  @BeforeEach
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
    // Set.of(anyUser.getId()) in the stub rejects null.
    anyUser.setId(1L);
    workspaceCtrller.setRecordManager(recordMgr);
  }

  // rspac-2073
  @Test
  public void viewMyTemplatesOrderedMyModificationDesc() throws InterruptedException {
    Set<BaseRecord> rawResults = generateResults();
    when(recordMgr.getViewableTemplates(Set.of(anyUser.getId()))).thenReturn(rawResults);
    mockAuthenticatedUserInSession();
    List<RecordInformation> results = workspaceCtrller.getUsersOwnTemplates().getData();
    assertTemplateOrdering(results);
  }

  // rspac-2073
  @Test
  public void getTemplatesSharedWithUser() throws InterruptedException {
    Set<BaseRecord> rawResults = generateResults();
    when(sharingMgr.getTemplatesSharedWithUser(anyUser)).thenReturn(new ArrayList<>(rawResults));
    mockAuthenticatedUserInSession();
    List<RecordInformation> results = workspaceCtrller.getTemplatesSharedWithUser().getData();
    assertTemplateOrdering(results);
  }

  @Test
  void savingSettingsWithAValidSearchStoresTheNewSearchAndSettings() {
    MockHttpSession session = sessionWithPreviousSearch();
    when(userMgr.getUserByUsername(anyUser.getUsername())).thenReturn(anyUser);
    when(inputValidator.validateAndGetErrorList(any(), any())).thenReturn(new ErrorList());

    ResponseEntity<Object> response =
        saveSettings(new String[] {"name"}, new String[] {"Zebrafish"}, session);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    WorkspaceListingConfig stored = (WorkspaceListingConfig) session.getAttribute(SETTINGS_KEY);
    assertThat(stored.getSrchOptions()).containsExactly("name");
    assertThat(stored.getSrchTerms()).containsExactly("Zebrafish");
    assertNewFolderViewAndPaging(stored);
  }

  @Test
  void savingSettingsWithARejectedSearchKeepsThePreviousSearchButTakesTheNewSettings() {
    MockHttpSession session = sessionWithPreviousSearch();
    when(userMgr.getUserByUsername(anyUser.getUsername())).thenReturn(anyUser);
    when(inputValidator.validateAndGetErrorList(any(), any()))
        .thenReturn(ErrorList.of("search term too short"));

    saveSettings(new String[] {"global"}, new String[] {"*abc"}, session);

    WorkspaceListingConfig stored = (WorkspaceListingConfig) session.getAttribute(SETTINGS_KEY);
    assertThat(stored.getSrchOptions()).containsExactly("global");
    assertThat(stored.getSrchTerms()).containsExactly("lab notebook");
    assertTrue(stored.isAdvancedSearch());
    assertEquals(SearchOperator.OR, stored.getOperator());
    assertNewFolderViewAndPaging(stored);
  }

  @Test
  void savingSettingsWithNoSearchClearsTheSearchWithoutValidating() {
    MockHttpSession session = sessionWithPreviousSearch();
    when(userMgr.getUserByUsername(anyUser.getUsername())).thenReturn(anyUser);

    saveSettings(null, null, session);

    WorkspaceListingConfig stored = (WorkspaceListingConfig) session.getAttribute(SETTINGS_KEY);
    assertThat(stored.getSrchOptions()).isEmpty();
    assertThat(stored.getSrchTerms()).isEmpty();
    assertNewFolderViewAndPaging(stored);
    verifyNoInteractions(inputValidator);
  }

  private MockHttpSession sessionWithPreviousSearch() {
    WorkspaceListingConfig previous = new WorkspaceListingConfig();
    previous.setSrchOptions(new String[] {"global"});
    previous.setSrchTerms(new String[] {"lab notebook"});
    previous.setAdvancedSearch(true);
    previous.setOperator(SearchOperator.OR);
    previous.setParentFolderId(1L);
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(SETTINGS_KEY, previous);
    return session;
  }

  private ResponseEntity<Object> saveSettings(
      String[] options, String[] terms, MockHttpSession session) {
    WorkspaceSettings settings = new WorkspaceSettings();
    settings.setParentFolderId(2L);
    settings.setCurrentViewMode(WorkspaceViewMode.TREE_VIEW);
    settings.setResultsPerPage(50);
    return workspaceCtrller.saveWorkspaceSettings(
        SETTINGS_KEY, options, terms, settings, anyUser::getUsername, session);
  }

  private void assertNewFolderViewAndPaging(WorkspaceListingConfig stored) {
    assertEquals(2L, stored.getParentFolderId());
    assertEquals(WorkspaceViewMode.TREE_VIEW, stored.getCurrentViewMode());
    assertEquals(50, stored.getPgCrit().getResultsPerPage());
  }

  private void assertTemplateOrdering(List<RecordInformation> results) {
    assertThat(results).hasSize(TEMPLATE_COUNT);
    for (int i = 0; i < results.size() - 1; i++) {
      int thisSuffix = Integer.parseInt(results.get(i).getName().split("-")[1]);
      int nextSuffix = Integer.parseInt(results.get(i + 1).getName().split("-")[1]);
      assertEquals(nextSuffix + 1, thisSuffix);
      assertTrue(
          results.get(i).getModificationDate().after(results.get(i + 1).getModificationDate()));
    }
  }

  private void mockAuthenticatedUserInSession() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
  }

  // names of docs end with '-i' where i is order of creation, ascending
  private Set<BaseRecord> generateResults() throws InterruptedException {
    Set<BaseRecord> rc = new HashSet<>();
    for (long i = 0; i < TEMPLATE_COUNT; i++) {
      StructuredDocument sdoc = TestFactory.createAnySD();
      Thread.sleep(1); // force mod-date to be different per document
      sdoc.setId(i);
      sdoc.setOwner(anyUser);
      sdoc.setName(RandomStringUtils.randomAlphabetic(5) + "-" + i);
      rc.add(sdoc);
    }
    return rc;
  }
}
