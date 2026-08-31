package com.researchspace.service.audit.search;

import static org.junit.Assert.assertEquals;

import com.researchspace.api.v1.controller.ApiActivitySrchConfig;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.testutils.TestFactory;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AuditTrailHandlerImplTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock IAuditTrailSearch logSearcher;
  @Mock AuditTrailActorVisibility actorVisibility;
  private @Mock AuditTrailService auditService;
  @InjectMocks AuditTrailHandlerImpl impl;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void configureUserRestrictionDoesNotPermitUnauthorised() {
    User subject = TestFactory.createAnyUser("subject");
    User other = TestFactory.createAnyUser("other");
    IAuditTrailSearchConfig cfg = new ApiActivitySrchConfig();
    cfg.getUsernames().add(other.getUsername());
    AuditTrailSearchElement expected = new AuditTrailSearchElement(cfg);
    expected.setUsernames(Set.of());
    Mockito.when(actorVisibility.restrict(cfg, subject)).thenReturn(expected);
    AuditTrailSearchElement internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(0, internalSearchEl.getUsernames().size());
  }

  @Test
  public void configureUserRestrictionIncludesSelfIfOmittedByGetViewableUsers() {
    User subject = TestFactory.createAnyUser("subject");
    IAuditTrailSearchConfig cfg = new ApiActivitySrchConfig();
    AuditTrailSearchElement expected = new AuditTrailSearchElement(cfg);
    expected.setUsernames(Set.of(subject.getUsername()));
    Mockito.when(actorVisibility.restrict(cfg, subject)).thenReturn(expected);
    AuditTrailSearchElement internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(1, internalSearchEl.getUsernames().size());
  }

  @Test
  public void configureUserRestrictionPermitsOthers() {
    User subject = TestFactory.createAnyUser("subject");
    User other = TestFactory.createAnyUser("other");
    IAuditTrailSearchConfig cfg = new ApiActivitySrchConfig();
    cfg.getUsernames().add(other.getUsername());
    AuditTrailSearchElement oneUser = new AuditTrailSearchElement(cfg);
    oneUser.setUsernames(Set.of(other.getUsername()));
    Mockito.when(actorVisibility.restrict(cfg, subject)).thenReturn(oneUser);
    AuditTrailSearchElement internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(1, internalSearchEl.getUsernames().size());

    cfg.getUsernames().add(subject.getUsername());
    AuditTrailSearchElement twoUsers = new AuditTrailSearchElement(cfg);
    twoUsers.setUsernames(Set.of(subject.getUsername(), other.getUsername()));
    Mockito.when(actorVisibility.restrict(cfg, subject)).thenReturn(twoUsers);
    internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(2, internalSearchEl.getUsernames().size());

    cfg.getUsernames().clear();
    AuditTrailSearchElement subjectOnly = new AuditTrailSearchElement(cfg);
    subjectOnly.setUsernames(Set.of(subject.getUsername()));
    Mockito.when(actorVisibility.restrict(cfg, subject)).thenReturn(subjectOnly);
    internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(1, internalSearchEl.getUsernames().size());
  }
}
