package com.researchspace.inventory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FilterExpression;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiV2InstrumentResourceTest {

  private static final AccessPolicy ACCESS = ApiV2InstrumentResource.DESCRIPTION.accessPolicy();

  private static AccessResult read(User caller) {
    return ACCESS.readAccess().check(new AccessContext(caller, Operation.READ, "instruments", 1L));
  }

  @Test
  void limitsEveryReadToInstrumentsThatAreNotDeleted() {
    Optional<FilterExpression> constraint = read(mock(User.class)).constraintOrEmpty();

    assertEquals(
        Optional.of(
            new FilterExpression.Comparison("deleted", Operator.EQUAL, List.of(false), false)),
        constraint);
  }

  @Test
  void refusesAnAnonymousRead() {
    assertTrue(read(null).isDenied());
  }

  @Test
  void refusesEveryMutation() {
    for (Operation operation : List.of(Operation.CREATE, Operation.UPDATE, Operation.DELETE)) {
      assertTrue(
          ACCESS
              .forOperation(operation)
              .check(new AccessContext(mock(User.class), operation, "instruments"))
              .isDenied(),
          "instruments must refuse " + operation);
    }
  }

  @Test
  void filtersOnNameThroughTheEmbeddedEditInfoProperty() {
    assertEquals(
        "editInfo.name", ApiV2InstrumentResource.DESCRIPTION.requireField("name").property());
    assertTrue(
        ApiV2InstrumentResource.DESCRIPTION
            .requireField("name")
            .operators()
            .contains(Operator.CONTAINS));
  }

  @Test
  void keepsTheDerivedGlobalIdOutOfQueries() {
    assertTrue(ApiV2InstrumentResource.DESCRIPTION.requireField("globalId").operators().isEmpty());
    assertFalse(ApiV2InstrumentResource.DESCRIPTION.requireField("globalId").sortable());
  }
}
