package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parses a caller filter that names a field reached through a relationship. */
class RsqlTargetFieldParserTest {

  private static final long SUBJECT_ID = 73L;
  private static final User SUBJECT = new User("subject");

  static {
    SUBJECT.setId(SUBJECT_ID);
  }

  private static final ResourceRegistry REGISTRY =
      new ResourceRegistry(
          List.of(
              ApiV2BookingConfigurationResource.DESCRIPTION,
              ApiV2InstrumentResource.DESCRIPTION,
              ApiV2UserResource.DESCRIPTION));

  private final RsqlFilterParser withTargets =
      new RsqlFilterParser(ApiV2BookingConfigurationResource.DESCRIPTION, REGISTRY);
  private final RsqlFilterParser withSubject =
      new RsqlFilterParser(
          ApiV2BookingConfigurationResource.DESCRIPTION,
          REGISTRY,
          new RuntimeFieldContext(List.of(), SUBJECT));
  private final RsqlFilterParser withoutTargets =
      new RsqlFilterParser(ApiV2BookingConfigurationResource.DESCRIPTION);

  @Test
  void resolvesMeOnStoredUserRelationshipIds() {
    assertEquals(
        new FilterExpression.Comparison(
            "createdBy.value", Operator.EQUAL, List.of(SUBJECT_ID), false),
        withSubject.parse("createdBy.value==me"));
    assertEquals(
        new FilterExpression.Comparison(
            "createdBy.value", Operator.EQUAL, List.of(SUBJECT_ID), false),
        withSubject.parse("createdBy.value=='me'"));
    assertEquals(
        new FilterExpression.Comparison(
            "createdBy.value", Operator.EQUAL, List.of(SUBJECT_ID), false),
        withSubject.parse("createdBy.value==\"me\""));
    assertEquals(
        new FilterExpression.Comparison(
            "createdBy.value", Operator.IN, List.of(SUBJECT_ID, 42L), false),
        withSubject.parse("createdBy.value=in=(me,42)"));
  }

  @Test
  void resolvesMeOnTheUserTargetsIdField() {
    assertEquals(
        new FilterExpression.Comparison("createdBy.id", Operator.EQUAL, List.of(SUBJECT_ID), false),
        withSubject.parse("createdBy.id==me"));
    assertEquals(
        new FilterExpression.Comparison(
            "createdBy.id", Operator.IN, List.of(SUBJECT_ID, 42L), false),
        withSubject.parse("createdBy.id=in=(me,42)"));
  }

  @Test
  void keepsMeLiteralOnUserTextFields() {
    assertEquals(
        new FilterExpression.Comparison("createdBy.username", Operator.EQUAL, List.of("me"), false),
        withSubject.parse("createdBy.username==me"));
    assertEquals(
        new FilterExpression.Comparison(
            "createdBy.firstName", Operator.EQUAL, List.of("me"), false),
        withSubject.parse("createdBy.firstName==me"));
    assertEquals(
        new FilterExpression.Comparison("createdBy.lastName", Operator.EQUAL, List.of("me"), false),
        withSubject.parse("createdBy.lastName==me"));
  }

  @Test
  void resolvesMeForExistingNegativeOperatorsOnly() {
    assertEquals(
        new FilterExpression.Comparison(
            "createdBy.value", Operator.NOT_EQUAL, List.of(SUBJECT_ID), false),
        withSubject.parse("createdBy.value!=me"));
    assertEquals(
        new FilterExpression.Comparison(
            "createdBy.value", Operator.NOT_IN, List.of(SUBJECT_ID, 42L), false),
        withSubject.parse("createdBy.value=out=(me,42)"));

    CollectionQueryException error =
        assertThrows(CollectionQueryException.class, () -> withSubject.parse("createdBy.id!=me"));
    assertEquals(CollectionQueryException.Reason.OPERATOR, error.getReason());
  }

  @Test
  void refusesMeOutsideUserRelationshipIds() {
    assertValueError(() -> withSubject.parse("target.value==me"));
    assertValueError(() -> withSubject.parse("target.id==me"));
    assertValueError(() -> withSubject.parse("id==me"));
    assertValueError(() -> withSubject.parse("createdBy.value==ME"));
  }

  @Test
  void refusesMeWithoutAPersistedSubject() {
    assertValueError(() -> withTargets.parse("createdBy.value==me"));
    assertValueError(
        () ->
            new RsqlFilterParser(
                    ApiV2BookingConfigurationResource.DESCRIPTION,
                    REGISTRY,
                    new RuntimeFieldContext(List.of(), new User("transient")))
                .parse("createdBy.id==me"));
  }

  @Test
  void doesNotCreateABareUserRelationshipSelector() {
    CollectionQueryException error =
        assertThrows(CollectionQueryException.class, () -> withSubject.parse("createdBy==me"));

    assertEquals(CollectionQueryException.Reason.FIELD, error.getReason());
  }

  @Test
  void acceptsAFieldOfTheRelationshipTarget() {
    assertEquals(
        new FilterExpression.Comparison(
            "target.name", Operator.CONTAINS, List.of("confocal"), false),
        withTargets.parse("target.name=contains=confocal"));
  }

  @Test
  void keepsTheExistingRelationshipSelectorsWorking() {
    FilterExpression parsed = withTargets.parse("target.relationTo==instruments");

    assertEquals("target.relationTo", ((FilterExpression.Comparison) parsed).field());
    assertEquals(Operator.EQUAL, ((FilterExpression.Comparison) parsed).operator());
  }

  @Test
  void refusesARelationshipFieldWithoutARegistry() {
    assertThrows(
        CollectionQueryException.class,
        () -> withoutTargets.parse("target.name=contains=confocal"));
  }

  @Test
  void refusesANegativeOperatorOnARelationshipField() {
    assertThrows(CollectionQueryException.class, () -> withTargets.parse("target.name!=confocal"));
  }

  @Test
  void refusesAFieldTheTargetDoesNotPublish() {
    assertThrows(CollectionQueryException.class, () -> withTargets.parse("target.globalId==IN1"));
    assertThrows(CollectionQueryException.class, () -> withTargets.parse("target.nosuch==x"));
  }

  /** An internal filter must stay unreachable, including through a relationship. */
  @Test
  void refusesAnInternalFilterOfTheTarget() {
    assertThrows(CollectionQueryException.class, () -> withTargets.parse("target.sharingAcl==lab"));
    assertThrows(
        CollectionQueryException.class, () -> withTargets.parse("target.ownerUsername==someone"));
  }

  private static void assertValueError(Runnable parse) {
    CollectionQueryException error = assertThrows(CollectionQueryException.class, parse::run);
    assertEquals(CollectionQueryException.Reason.VALUE, error.getReason());
  }
}
