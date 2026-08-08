package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.permissions.SecurityLogger;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Collection-, row-, and field-level access-function enforcement at its single choke point. */
class ApiV2ResourceAccessTest {

  record Widget(Long id, String secret) {}

  private static final Field<Widget, Long> ID =
      Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Widget::id);

  private final ResourceOperations<Widget, Long> operations = mock(ResourceOperations.class);

  private CollectionDescription<Widget> describe(AccessPolicy policy, AccessFunction secretAccess) {
    return new CollectionDescription<>(
        "widgets",
        Widget.class,
        List.of(
            ID,
            Field.readOnly("secret", "secret", CollectionFieldTypes.text(), Widget::secret)
                .readableBy(secretAccess)),
        List.of(),
        "id",
        List.of(new Sort("id", true)),
        policy);
  }

  private ApiV2ResourceRegistration<Widget, Long> register(CollectionDescription<Widget> widgets) {
    ApiV2ResourceSpec<Widget, Long> spec =
        new ApiV2ResourceSpec<>(
            widgets,
            operations,
            Long::valueOf,
            "errors.api.v2.invalidRequest",
            "errors.api.v2.invalidRequest");
    return spec.bind(
        new ResourceRegistry(List.of(widgets)), ApiV2RelationshipResolver.unavailable());
  }

  private static ResourceRequest request(FilterExpression filter, List<Sort> sort) {
    return new ResourceRequest(
        filter, sort, new ResourceRequest.Page(1, 20), FieldSelection.all(), IncludeTree.empty());
  }

  private static User user(boolean sysadmin) {
    User user = mock(User.class);
    when(user.hasRole(Role.SYSTEM_ROLE)).thenReturn(sysadmin);
    return user;
  }

  @Test
  @DisplayName("an anonymous read of an authenticated collection is 401, not 403")
  void anonymousReadOfAuthenticatedCollectionIsUnauthorized() {
    ApiV2ResourceRegistration<Widget, Long> widgets =
        register(describe(AccessPolicy.authenticated(), AccessFunction.anyone()));

    assertThrows(
        ApiV2AuthenticationException.class, () -> widgets.list(request(null, List.of()), null));
    verify(operations, never()).find(any(), nullable(User.class));
  }

  @Test
  @DisplayName("a known but refused caller is 403")
  void refusedCallerIsForbidden() {
    ApiV2ResourceRegistration<Widget, Long> widgets =
        register(
            describe(AccessPolicy.readOnly(AccessFunction.sysadmin()), AccessFunction.anyone()));

    assertThrows(
        AuthorizationException.class, () -> widgets.list(request(null, List.of()), user(false)));
  }

  @Test
  @DisplayName("a policy constraint is ANDed into the caller's filter and reaches the adapter")
  void policyConstraintIsFoldedIntoTheQuery() {
    FilterExpression ownRows =
        new FilterExpression.Comparison("id", Operator.EQUAL, List.of(7L), false);
    FilterExpression callerFilter =
        new FilterExpression.Comparison("id", Operator.GREATER_THAN, List.of(1L), false);
    CollectionDescription<Widget> widgets =
        describe(
            AccessPolicy.readOnly(documented(context -> AccessResult.allowedWhere(ownRows))),
            AccessFunction.anyone());
    when(operations.find(any(), nullable(User.class))).thenReturn(new ResourcePage<>(List.of(), 0));

    register(widgets).list(request(callerFilter, List.of()), user(false));

    ArgumentCaptor<ResourceRequest> captor = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(operations).find(captor.capture(), any(User.class));
    FilterExpression applied = captor.getValue().filter();
    assertTrue(applied instanceof FilterExpression.And, "expected a conjunction, got " + applied);
    assertEquals(List.of(ownRows, callerFilter), ((FilterExpression.And) applied).children());
  }

  /**
   * The identical-error rule: a caller must not be able to tell "no such field" from "not yours",
   * or they can probe for field names.
   */
  @Test
  @DisplayName("filtering or sorting on an unreadable field fails exactly like an unknown field")
  void unreadableQueryFieldFailsLikeAnUnknownField() {
    ApiV2ResourceRegistration<Widget, Long> widgets =
        register(
            describe(AccessPolicy.readOnly(AccessFunction.anyone()), AccessFunction.sysadmin()));
    FilterExpression onSecret =
        new FilterExpression.Comparison("secret", Operator.EQUAL, List.of("x"), false);

    CollectionQueryException filtering =
        assertThrows(
            CollectionQueryException.class,
            () -> widgets.list(request(onSecret, List.of()), user(false)));
    CollectionQueryException sorting =
        assertThrows(
            CollectionQueryException.class,
            () -> widgets.list(request(null, List.of(new Sort("secret", true))), user(false)));

    assertEquals(CollectionQueryException.Reason.FIELD, filtering.getReason());
    assertEquals(filtering.getReason(), sorting.getReason());
  }

  @Test
  @DisplayName("an unreadable field is omitted from the document rather than refused")
  void unreadableFieldIsOmittedFromOutput() {
    CollectionDescription<Widget> widgets =
        describe(AccessPolicy.readOnly(AccessFunction.anyone()), AccessFunction.sysadmin());
    when(operations.find(any(), nullable(User.class)))
        .thenReturn(new ResourcePage<>(List.of(new Widget(7L, "classified")), 1));

    ApiV2ListResult<Map<String, Object>> anonymous =
        register(widgets).list(request(null, List.of()), null);

    Map<String, Object> document = anonymous.docs().get(0);
    assertEquals(7L, document.get("id"));
    assertFalse(document.containsKey("secret"), "secret must not be rendered: " + document);
  }

  @Test
  void rowSpecificFieldAccessIsEvaluatedForEveryListDocument() {
    AccessFunction ownRowOnly =
        AccessFunction.when(context -> context.targetId() == null || context.targets(7L));
    CollectionDescription<Widget> widgets =
        describe(AccessPolicy.readOnly(AccessFunction.anyone()), ownRowOnly);
    when(operations.find(any(), nullable(User.class)))
        .thenReturn(
            new ResourcePage<>(List.of(new Widget(7L, "visible"), new Widget(8L, "hidden")), 2));

    List<Map<String, Object>> documents =
        register(widgets).list(request(null, List.of()), user(false)).docs();

    assertEquals("visible", documents.get(0).get("secret"));
    assertFalse(documents.get(1).containsKey("secret"));
  }

  @Test
  @DisplayName("a field access function returning a row constraint fails closed")
  void rowConstraintCannotBeAppliedAsFieldAccess() {
    FilterExpression constraint =
        new FilterExpression.Comparison("id", Operator.EQUAL, List.of(7L), false);
    CollectionDescription<Widget> widgets =
        describe(
            AccessPolicy.readOnly(AccessFunction.anyone()),
            documented(context -> AccessResult.allowedWhere(constraint)));
    when(operations.find(any(), nullable(User.class)))
        .thenReturn(new ResourcePage<>(List.of(new Widget(7L, "classified")), 1));

    assertThrows(
        IllegalStateException.class,
        () -> register(widgets).list(request(null, List.of()), user(false)));
  }

  @Test
  @DisplayName("a row constraint also scopes get, so a row outside it is a 404")
  void rowConstraintScopesGet() {
    FilterExpression ownRows =
        new FilterExpression.Comparison("id", Operator.EQUAL, List.of(7L), false);
    ApiV2ResourceRegistration<Widget, Long> widgets =
        register(
            describe(
                AccessPolicy.readOnly(documented(context -> AccessResult.allowedWhere(ownRows))),
                AccessFunction.anyone()));
    when(operations.find(any(), nullable(User.class))).thenReturn(new ResourcePage<>(List.of(), 0));

    assertThrows(
        NotFoundException.class, () -> widgets.get("9", request(null, List.of()), user(false)));

    // findById would have ignored the constraint entirely and handed back row 9.
    verify(operations, never()).findById(any(), any());
    ArgumentCaptor<ResourceRequest> captor = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(operations).find(captor.capture(), any(User.class));
    assertEquals(
        List.of(ownRows, new FilterExpression.Comparison("id", Operator.EQUAL, List.of(9L), false)),
        ((FilterExpression.And) captor.getValue().filter()).children());
  }

  @Test
  @DisplayName("a relationship authorization failure is hidden from the client but audited")
  void relationshipAuthorizationFailureIsAudited() {
    User actor = user(false);
    when(actor.getUsername()).thenReturn("ada");
    when(operations.findById(7L, actor)).thenThrow(new AuthorizationException("denied"));
    ApiV2ResourceRegistration<Widget, Long> widgets =
        register(describe(AccessPolicy.readOnly(AccessFunction.anyone()), AccessFunction.anyone()));
    StringAppenderForTestLogging securityLog =
        CoreTestUtils.configureStringLogger(LogManager.getLogger(SecurityLogger.class));

    assertTrue(widgets.resolveReadable(7L, actor).isEmpty());

    assertTrue(securityLog.logContents.contains("authorization failure for user [ada]"));
    assertTrue(securityLog.logContents.contains("resource [widgets]"));
  }

  @Test
  @DisplayName("a row constraint on a single-row write fails closed rather than going unenforced")
  void rowConstraintOnSingleRowWriteFailsClosed() {
    FilterExpression ownRows =
        new FilterExpression.Comparison("id", Operator.EQUAL, List.of(7L), false);
    ApiV2ResourceRegistration<Widget, Long> widgets =
        register(
            describe(
                new AccessPolicy(
                    AccessFunction.authenticated(),
                    AccessFunction.authenticated(),
                    documented(context -> AccessResult.allowedWhere(ownRows)),
                    AccessFunction.authenticated(),
                    AccessFunction.authenticated()),
                AccessFunction.anyone()));

    assertThrows(IllegalStateException.class, () -> widgets.update("9", null, user(false)));
    verify(operations, never()).update(any(), any(), any());
  }

  /**
   * The write-only case. A field declared {@code readAccess = NEVER} must not come back in the
   * response to the write that set it, which is what made the old all-fields write rendering
   * unsafe.
   */
  @Test
  @DisplayName("a write-only field is absent from the create and delete responses")
  void writeOnlyFieldIsAbsentFromWriteResponses() {
    // The secret has to be writable for this to be the write-only case at all.
    CollectionDescription<Widget> widgets =
        new CollectionDescription<>(
            "widgets",
            Widget.class,
            List.of(
                ID,
                Field.writable(
                        "secret",
                        "secret",
                        CollectionFieldTypes.text(),
                        Widget::secret,
                        (widget, value) -> {})
                    .readableBy(AccessFunction.never())),
            List.of(),
            "id",
            List.of(new Sort("id", true)),
            AccessPolicy.authenticated());
    Widget saved = new Widget(7L, "hunter2");
    when(operations.create(any(), any())).thenReturn(saved);
    when(operations.delete(any(), any())).thenReturn(Optional.of(saved));
    ApiV2ResourceRegistration<Widget, Long> registration = register(widgets);
    User actor = user(true);

    Map<String, Object> created =
        registration.create(new ObjectMapper().createObjectNode().put("secret", "hunter2"), actor);
    Map<String, Object> deleted = registration.delete("7", actor);

    // Even a sysadmin must not see it: never() ignores the caller entirely.
    assertFalse(created.containsKey("secret"), "create echoed the secret back: " + created);
    assertFalse(deleted.containsKey("secret"), "delete echoed the secret back: " + deleted);
    // The id must survive, or a client cannot tell what it just created.
    assertEquals(7L, created.get("id"));
  }

  /**
   * The motivating case: a system administrator or the user themselves may change the secret,
   * nobody else. Needs field-level write access and the target id together, which is why {@code
   * AccessContext} carries one.
   */
  @Test
  @DisplayName("a sysadmin or the row's own user may write the field, a third party may not")
  void writeAccessCombinesRoleAndOwnRow() {
    AccessFunction sysadminOrSelf =
        AccessFunction.when(
            context ->
                context.isAuthenticated()
                    && (context.user().hasRole(Role.SYSTEM_ROLE)
                        || context.targets(context.user().getId())));
    ApiV2ResourceRegistration<Widget, Long> widgets = register(secretWritableBy(sysadminOrSelf));
    when(operations.update(any(), any(), any())).thenReturn(Optional.of(new Widget(7L, "changed")));

    User owner = user(false);
    when(owner.getId()).thenReturn(7L);
    User stranger = user(false);
    when(stranger.getId()).thenReturn(99L);

    // The owner writes their own row.
    assertEquals(7L, widgets.update("7", secretBody(), owner).get("id"));
    // A sysadmin writes anyone's row.
    assertEquals(7L, widgets.update("7", secretBody(), user(true)).get("id"));

    // A third party is refused, and identically to a field that is not writable at all.
    DocumentValidationException refused =
        assertThrows(
            DocumentValidationException.class, () -> widgets.update("7", secretBody(), stranger));
    DocumentValidationException notWritable =
        assertThrows(
            DocumentValidationException.class,
            () ->
                register(describe(AccessPolicy.authenticated(), AccessFunction.anyone()))
                    .update("7", secretBody(), stranger));
    assertEquals(notWritable.getViolations(), refused.getViolations());
  }

  private static JsonNode secretBody() {
    return new ObjectMapper().createObjectNode().put("secret", "changed");
  }

  private static AccessFunction documented(AccessFunction function) {
    return AccessFunction.documented("Test access rule.", Set.of(), function);
  }

  private CollectionDescription<Widget> secretWritableBy(AccessFunction writeAccess) {
    return new CollectionDescription<>(
        "widgets",
        Widget.class,
        List.of(
            ID,
            Field.writable(
                    "secret",
                    "secret",
                    CollectionFieldTypes.text(),
                    Widget::secret,
                    (widget, value) -> {})
                .writableBy(writeAccess)),
        List.of(),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.authenticated());
  }

  @Test
  @DisplayName("custom create access receives the complete parsed candidate")
  void customCreateAccessReceivesParsedInput() {
    AccessFunction allowedSecret =
        documented(
            context ->
                "allowed".equals(context.requireInput().values().get("secret"))
                    ? AccessResult.allowed()
                    : AccessResult.denied(AccessPolicy.FORBIDDEN));
    AccessPolicy policy =
        new AccessPolicy(
            AccessFunction.authenticated(),
            allowedSecret,
            AccessFunction.authenticated(),
            AccessFunction.authenticated(),
            AccessFunction.never());
    CollectionDescription<Widget> description =
        new CollectionDescription<>(
            "widgets",
            Widget.class,
            List.of(
                ID,
                Field.writable(
                    "secret",
                    "secret",
                    CollectionFieldTypes.text(),
                    Widget::secret,
                    (widget, value) -> {})),
            List.of(),
            "id",
            List.of(new Sort("id", true)),
            policy);
    ApiV2ResourceRegistration<Widget, Long> widgets = register(description);
    User actor = user(false);
    when(operations.create(any(), any())).thenReturn(new Widget(7L, "allowed"));

    assertEquals(7L, widgets.create(secretBody("allowed"), actor).get("id"));
    assertThrows(AuthorizationException.class, () -> widgets.create(secretBody("denied"), actor));
  }

  @Test
  @DisplayName("custom bulk create access receives every candidate in request order")
  void customBulkCreateAccessReceivesAllParsedInputs() throws Exception {
    AccessFunction twoOrderedDocuments =
        documented(
            context ->
                context.inputs().stream()
                        .map(document -> document.values().get("secret"))
                        .toList()
                        .equals(List.of("first", "second"))
                    ? AccessResult.allowed()
                    : AccessResult.denied(AccessPolicy.FORBIDDEN));
    AccessPolicy policy =
        new AccessPolicy(
            AccessFunction.authenticated(),
            twoOrderedDocuments,
            AccessFunction.authenticated(),
            AccessFunction.authenticated(),
            AccessFunction.never());
    CollectionDescription<Widget> description =
        new CollectionDescription<>(
            "widgets",
            Widget.class,
            List.of(
                ID,
                Field.writable(
                    "secret",
                    "secret",
                    CollectionFieldTypes.text(),
                    Widget::secret,
                    (widget, value) -> {})),
            List.of(),
            "id",
            List.of(new Sort("id", true)),
            policy);
    ApiV2ResourceRegistration<Widget, Long> widgets = register(description);
    when(operations.createMany(any(), any()))
        .thenReturn(List.of(new Widget(7L, "first"), new Widget(8L, "second")));

    assertEquals(
        2,
        widgets
            .createMany(
                new ObjectMapper()
                    .readTree("{\"docs\":[{\"secret\":\"first\"},{\"secret\":\"second\"}]}"),
                user(false))
            .docs()
            .size());
  }

  @Test
  @DisplayName("custom field create access receives sibling input")
  void customFieldCreateAccessReceivesSiblingInput() {
    AccessFunction allowedSecret =
        documented(
            context ->
                "allowed".equals(context.requireInput().values().get("secret"))
                    ? AccessResult.allowed()
                    : AccessResult.denied(AccessPolicy.FORBIDDEN));
    CollectionDescription<Widget> description =
        new CollectionDescription<>(
            "widgets",
            Widget.class,
            List.of(
                ID,
                Field.writable(
                        "secret",
                        "secret",
                        CollectionFieldTypes.text(),
                        Widget::secret,
                        (widget, value) -> {})
                    .creatableBy(allowedSecret)),
            List.of(),
            "id",
            List.of(new Sort("id", true)),
            AccessPolicy.authenticated());
    ApiV2ResourceRegistration<Widget, Long> widgets = register(description);
    User actor = user(false);
    when(operations.create(any(), any())).thenReturn(new Widget(7L, "allowed"));

    assertEquals(7L, widgets.create(secretBody("allowed"), actor).get("id"));
    assertThrows(
        DocumentValidationException.class, () -> widgets.create(secretBody("denied"), actor));
  }

  private static JsonNode secretBody(String value) {
    return new ObjectMapper().createObjectNode().put("secret", value);
  }

  @Test
  @DisplayName("a sysadmin sees the restricted field")
  void sysadminSeesTheRestrictedField() {
    CollectionDescription<Widget> widgets =
        describe(AccessPolicy.readOnly(AccessFunction.anyone()), AccessFunction.sysadmin());
    when(operations.find(any(), nullable(User.class)))
        .thenReturn(new ResourcePage<>(List.of(new Widget(7L, "classified")), 1));

    Map<String, Object> document =
        register(widgets).list(request(null, List.of()), user(true)).docs().get(0);

    assertEquals("classified", document.get("secret"));
  }
}
