package com.researchspace.inventory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription.FieldSchema;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import java.util.List;
import java.util.Map;
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

  @Test
  void exposesTheImmediateParentContainerAsTheInstrumentLocation() {
    Instrument instrument = mock(Instrument.class);
    Container parent = mock(Container.class);
    when(instrument.getParentContainer()).thenReturn(parent);
    when(parent.getName()).thenReturn("Imaging lab");
    when(parent.getGlobalIdentifier()).thenReturn("IC456");

    Map<String, Object> location =
        ApiV2InstrumentResource.DESCRIPTION.toDocument(
            instrument, field -> field.startsWith("parentContainer"));

    assertEquals("Imaging lab", location.get("parentContainerName"));
    assertEquals("IC456", location.get("parentContainerGlobalId"));

    for (String field : List.of("parentContainerName", "parentContainerGlobalId")) {
      FieldSchema schema = ApiV2InstrumentResource.DESCRIPTION.requireField(field).schema(ACCESS);
      assertTrue(schema.nullable());
      assertTrue(schema.readOnly());
      assertTrue(schema.filterOperators().isEmpty());
      assertFalse(schema.sortable());
      assertTrue(schema.openApi().deprecated());
      assertTrue(schema.openApi().description().contains("future parentContainer relationship"));
    }
  }

  @Test
  void reportsNoLocationWhenTheInstrumentHasNoParentContainer() {
    Instrument instrument = mock(Instrument.class);

    Map<String, Object> location =
        ApiV2InstrumentResource.DESCRIPTION.toDocument(
            instrument, field -> field.startsWith("parentContainer"));

    assertNull(location.get("parentContainerName"));
    assertNull(location.get("parentContainerGlobalId"));
  }
}
