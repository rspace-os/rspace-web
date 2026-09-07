package com.researchspace.service.audit.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.controller.ApiActivitySrchConfig;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.testutils.TestFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AuditTrailHandlerImplTest {
  @Mock IAuditTrailSearch logSearcher;
  @Mock AuditTrailActorVisibility actorVisibility;
  private @Mock AuditTrailService auditService;
  @InjectMocks AuditTrailHandlerImpl impl;

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
