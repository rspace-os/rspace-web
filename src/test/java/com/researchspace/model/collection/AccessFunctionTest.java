package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription.Operator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccessFunctionTest {

  @Test
  void decoratorPreservesTheDecisionAndExposesArbitraryDocumentation() {
    AccessResult decision = AccessResult.allowed();
    AccessFunction decorated =
        AccessFunction.documented(
            "Any collection-specific explanation can be supplied here.",
            Set.of("errors.example.denied"),
            ignored -> decision);

    assertSame(decision, decorated.check(context(null, null)));
    assertEquals(
        new AccessDocumentation(
            "Any collection-specific explanation can be supplied here.",
            Set.of("errors.example.denied")),
        decorated.documentation().orElseThrow());
  }

  @Test
  void commonAccessFunctionsAreDocumented() {
    List.of(
            AccessFunction.anyone(),
            AccessFunction.authenticated(),
            AccessFunction.sysadmin(),
            AccessFunction.selfOnly(),
            AccessFunction.sysadminOrSelf(),
            AccessFunction.never())
        .forEach(
            function -> {
              assertTrue(function.documentation().isPresent());
              assertTrue(!function.documentation().orElseThrow().description().isBlank());
            });
  }

  @Test
  void accessPoliciesRejectUndocumentedFunctions() {
    AccessFunction undocumented = ignored -> AccessResult.allowed();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccessPolicy(
                undocumented,
                AccessFunction.authenticated(),
                AccessFunction.authenticated(),
                AccessFunction.authenticated(),
                AccessFunction.authenticated()));
  }

  @Test
  void hardAndSoftDeleteHaveIndependentPolicies() {
    AccessPolicy policy =
        new AccessPolicy(
            AccessFunction.authenticated(),
            AccessFunction.authenticated(),
            AccessFunction.authenticated(),
            AccessFunction.sysadmin(),
            AccessFunction.never());
    User systemAdministrator = user(42L, true);

    assertInstanceOf(
        AccessResult.Allowed.class,
        policy
            .deleteAccess()
            .check(
                new AccessContext(systemAdministrator, AccessContext.Operation.DELETE, "widgets")));
    assertInstanceOf(
        AccessResult.Denied.class,
        policy
            .softDeleteAccess()
            .check(
                new AccessContext(
                    systemAdministrator, AccessContext.Operation.SOFT_DELETE, "widgets")));
  }

  @Test
  void selfOnlyRequiresAuthenticationAndConstrainsRowsToTheCallerId() {
    AccessFunction selfOnly = AccessFunction.selfOnly("ownerId");

    AccessResult.Denied anonymous =
        assertInstanceOf(AccessResult.Denied.class, selfOnly.check(context(null, null)));
    assertEquals(AccessPolicy.AUTHENTICATION_REQUIRED, anonymous.reasonCode());

    User caller = user(42L, false);
    AccessResult.AllowedWhere allowed =
        assertInstanceOf(AccessResult.AllowedWhere.class, selfOnly.check(context(caller, 99L)));
    assertEquals(
        new FilterExpression.Comparison("ownerId", Operator.EQUAL, List.of(42L), false),
        allowed.constraint());
  }

  @Test
  void sysadminOrSelfDoesNotConstrainSystemAdministrators() {
    AccessResult result = AccessFunction.sysadminOrSelf().check(context(user(42L, true), 99L));

    assertInstanceOf(AccessResult.Allowed.class, result);
  }

  private static AccessContext context(User user, Object targetId) {
    return new AccessContext(user, AccessContext.Operation.READ, "users", targetId);
  }

  private static User user(Long id, boolean sysadmin) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    when(user.hasRole(Role.SYSTEM_ROLE)).thenReturn(sysadmin);
    return user;
  }
}
