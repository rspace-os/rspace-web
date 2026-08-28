package com.researchspace.service.audit.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.controller.ApiActivitySrchConfig;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AuditTrailActorVisibilityTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock private UserManager userManager;
  @Mock private ISearchResults<User> searchResults;

  private AuditTrailActorVisibility visibility;

  @Before
  public void setUp() {
    visibility = new AuditTrailActorVisibility(userManager);
  }

  @Test
  public void sysadminKeepsUnrestrictedActorSet() {
    User sysadmin = TestFactory.createAnyUser("sysadmin");
    sysadmin.addRole(Role.SYSTEM_ROLE);

    AuditTrailSearchElement result = visibility.restrict(new ApiActivitySrchConfig(), sysadmin);

    assertTrue(result.getUsernames().isEmpty());
    Mockito.verifyNoInteractions(userManager);
  }

  @Test
  public void ordinaryUserGetsIntersectionIncludingCaller() {
    User subject = TestFactory.createAnyUser("subject");
    User other = TestFactory.createAnyUser("other");
    stubViewable(subject, List.of(other));
    ApiActivitySrchConfig config = new ApiActivitySrchConfig();
    config.getUsernames().addAll(Set.of(subject.getUsername(), other.getUsername(), "hidden"));

    AuditTrailSearchElement result = visibility.restrict(config, subject);

    assertEquals(Set.of(subject.getUsername(), other.getUsername()), result.getUsernames());
  }

  @Test
  public void ordinaryUserCanHaveEmptyAuthorizedIntersection() {
    User subject = TestFactory.createAnyUser("subject");
    stubViewable(subject, List.of());
    ApiActivitySrchConfig config = new ApiActivitySrchConfig();
    config.getUsernames().add("hidden");

    AuditTrailSearchElement result = visibility.restrict(config, subject);

    assertTrue(result.getUsernames().isEmpty());
  }

  private void stubViewable(User subject, List<User> users) {
    Mockito.when(
            userManager.getViewableUsers(
                Mockito.eq(subject), Mockito.any(PaginationCriteria.class)))
        .thenReturn(searchResults);
    Mockito.when(searchResults.getResults()).thenReturn(users);
  }
}
