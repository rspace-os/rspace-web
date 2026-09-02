package com.researchspace.service.audit.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.controller.ApiActivitySrchConfig;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AuditTrailHandlerImplTest {
  @Mock UserManager userManager;
  @Mock IAuditTrailSearch logSearcher;
  @Mock ISearchResults<User> searchResults;
  private @Mock AuditTrailService auditService;
  @InjectMocks AuditTrailHandlerImpl impl;

  @Test
  public void configureUserRestrictionDoesNotPermitUnauthorised() {
    User subject = TestFactory.createAnyUser("subject");
    User other = TestFactory.createAnyUser("other");
    setUpSearchResults(subject);
    Mockito.when(searchResults.getResults()).thenReturn(TransformerUtils.toList(subject));
    IAuditTrailSearchConfig cfg = new ApiActivitySrchConfig();
    cfg.getUsernames().add(other.getUsername());
    AuditTrailSearchElement internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(0, internalSearchEl.getUsernames().size());
  }

  @Test
  public void configureUserRestrictionIncludesSelfIfOmittedByGetViewableUsers() {
    User subject = TestFactory.createAnyUser("subject");
    setUpSearchResults(subject);
    // e.g  if you're a pi not in a group
    Mockito.when(searchResults.getResults()).thenReturn(new ArrayList<>());
    IAuditTrailSearchConfig cfg = new ApiActivitySrchConfig();
    AuditTrailSearchElement internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(1, internalSearchEl.getUsernames().size());
  }

  @Test
  public void configureUserRestrictionPermitsOthers() {
    User subject = TestFactory.createAnyUser("subject");
    User other = TestFactory.createAnyUser("other");
    setUpSearchResults(subject);
    Mockito.when(searchResults.getResults()).thenReturn(TransformerUtils.toList(subject, other));
    IAuditTrailSearchConfig cfg = new ApiActivitySrchConfig();
    cfg.getUsernames().add(other.getUsername());
    AuditTrailSearchElement internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(1, internalSearchEl.getUsernames().size());

    cfg.getUsernames().add(subject.getUsername());
    internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(2, internalSearchEl.getUsernames().size());

    cfg.getUsernames().clear();
    Mockito.when(searchResults.getResults()).thenReturn(TransformerUtils.toList(subject));
    internalSearchEl = impl.configureUserRestriction(cfg, subject);
    assertEquals(1, internalSearchEl.getUsernames().size());
  }

  private void setUpSearchResults(User subject) {
    Mockito.when(
            userManager.getViewableUsers(
                Mockito.eq(subject), Mockito.any(PaginationCriteria.class)))
        .thenReturn(searchResults);
  }
}
