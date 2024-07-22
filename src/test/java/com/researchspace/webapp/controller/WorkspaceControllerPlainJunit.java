package com.researchspace.webapp.controller;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.UserManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
public class WorkspaceControllerPlainJunit {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();
  @Mock RecordManager recordMgr;
  @Mock UserManager userMgr;
  @Mock RecordSharingManager sharingMgr;

  @InjectMocks WorkspaceController workspaceCtrller;
  User anyUser;

  @Before
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
    workspaceCtrller.setRecordManager(recordMgr);
  }

  @After
  public void tearDown() throws Exception {}

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
    for (int i = 0; i < results.size() - 1; i++) {
      Integer thisSuffixInteger = Integer.parseInt(results.get(i).getName().split("-")[1]);
      Integer nextSuffixInteger = Integer.parseInt(results.get(i + 1).getName().split("-")[1]);
      assertTrue(thisSuffixInteger == nextSuffixInteger + 1);
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
    for (long i = 0; i < 5; i++) {
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
