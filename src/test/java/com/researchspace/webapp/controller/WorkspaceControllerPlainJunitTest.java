package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
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

@ExtendWith(MockitoExtension.class)
public class WorkspaceControllerPlainJunitTest {
  private static final int TEMPLATE_COUNT = 5;

  @Mock RecordManager recordMgr;
  @Mock UserManager userMgr;
  @Mock RecordSharingManager sharingMgr;

  @InjectMocks WorkspaceController workspaceCtrller;
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

  private void assertTemplateOrdering(List<RecordInformation> results) {
    assertEquals(TEMPLATE_COUNT, results.size());
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
