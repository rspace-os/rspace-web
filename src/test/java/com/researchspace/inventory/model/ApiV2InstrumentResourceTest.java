package com.researchspace.inventory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FieldSchema;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRenderer;
import com.researchspace.model.inventory.Container.ContainerType;
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
    Optional<QueryConstraint> constraint = read(mock(User.class)).constraintOrEmpty();

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
    var name = ApiV2InstrumentResource.DESCRIPTION.requireField("name");
    assertEquals("editInfo.name", name.property());
    assertTrue(name.operators().contains(Operator.CONTAINS));
  }

  @Test
  void keepsTheDerivedGlobalIdOutOfQueries() {
    assertTrue(ApiV2InstrumentResource.DESCRIPTION.requireField("globalId").operators().isEmpty());
    assertFalse(ApiV2InstrumentResource.DESCRIPTION.requireField("globalId").sortable());
  }

  @Test
  void exposesItsParentOnlyThroughTheContainerRelationship() {
    var description = ApiV2InstrumentResource.DESCRIPTION;
    assertEquals(
        List.of("id", "name", "globalId", "deleted"),
        description.fields().stream().map(Field::name).toList());
    assertEquals(
        "parentLocation.container.id",
        description.requireRelationship("parentContainer").binding().idProperty());
  }

  @Test
  void expandsReadableParentAndHidesUnreadableParent() {
    Container parent = new Container(ContainerType.WORKBENCH);
    parent.setId(12L);
    parent.setName("Bench");
    Instrument instrument = new Instrument();
    instrument.setId(7L);
    instrument.setName("Scope");
    instrument.moveToNewParent(parent);
    var description = ApiV2InstrumentResource.DESCRIPTION;
    var registry = new ResourceRegistry(List.of(description, ApiV2ContainerResource.DESCRIPTION));
    var renderer = new ResourceRenderer(registry);
    var includes = IncludeTree.toDepth(description, registry, 2);
    var hidden =
        renderer.render(
            instrument,
            description,
            FieldSelection.all(),
            includes,
            (resource, id) -> Optional.empty());
    assertTrue(hidden.containsKey("parentContainer"));
    assertNull(hidden.get("parentContainer"));
    var visible =
        renderer.render(
            instrument,
            description,
            FieldSelection.all(),
            includes,
            (resource, id) ->
                Optional.of(new ResourceRenderer.ResolvedTarget(parent, FieldSelection.all())));
    assertEquals(
        Map.of(
            "relationTo",
            "containers",
            "value",
            Map.of(
                "id",
                12L,
                "globalId",
                "BE12",
                "name",
                "Bench",
                "containerType",
                "WORKBENCH",
                "deleted",
                false)),
        visible.get("parentContainer"));
  }
}
