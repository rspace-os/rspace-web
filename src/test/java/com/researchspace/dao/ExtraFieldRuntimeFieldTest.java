package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.service.inventory.ExtraFieldRuntimeManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import com.researchspace.service.inventory.impl.ExtraFieldRuntimeManagerImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ExtraFieldRuntimeFieldTest extends SpringTransactionalTest {

  @Autowired private InstrumentDao instrumentDao;
  @Autowired private InstrumentReadAccess instrumentReadAccess;
  @Autowired private ExtraFieldDao extraFieldDao;

  private ExtraFieldRuntimeManager<Instrument> extraFields;

  @Before
  public void setUpProvider() {
    extraFields =
        new ExtraFieldRuntimeManagerImpl<>(
            extraFieldDao,
            Instrument.class,
            com.researchspace.inventory.model.ApiV2InstrumentResource.DESCRIPTION,
            "instrumentEntity",
            instrumentReadAccess::check);
  }

  private Instrument instrumentWith(
      User owner, String name, String fieldName, String value, boolean numeric) {
    Instrument instrument = instrumentDao.get(createBasicInstrumentForUser(owner, name).getId());
    ExtraField field =
        recordFactory.createExtraField(
            fieldName, numeric ? FieldType.NUMBER : FieldType.TEXT, owner, instrument);
    field.setData(value);
    instrument.addExtraField(field);
    instrumentDao.save(instrument);
    sessionFactory.getCurrentSession().flush();
    return instrument;
  }

  private AccessResult readAccess(User user) {
    return instrumentReadAccess.check(new AccessContext(user, Operation.READ, "instruments"));
  }

  private List<RuntimeFieldDefinition> catalog(User actor) {
    return extraFields
        .discover(actor, new RuntimeFieldCatalogQuery(null, Set.of(), 1, 50))
        .fields();
  }

  private RuntimeFieldDefinition definition(User actor, String label, RuntimeFieldValueType type) {
    return catalog(actor).stream()
        .filter(field -> field.label().equals(label) && field.type() == type)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No definition " + label + " of " + type));
  }

  private List<String> matches(
      User actor, ResolvedRuntimeField field, Operator operator, Object value) {
    ResourceRequest request =
        new ResourceRequest(
            new FilterExpression.Comparison(field.selector(), operator, List.of(value), false),
            null,
            List.of(new Sort("name", true), new Sort("id", true)),
            new ResourceRequest.Page(1, 20),
            ResourceFieldSelections.root(FieldSelection.all()),
            IncludeTree.empty(),
            new RuntimeFieldSelection(Map.of(field.selector(), field), Set.of()));
    return instrumentDao.getReadableResources(request, readAccess(actor)).resources().stream()
        .map(Instrument::getName)
        .toList();
  }

  private ResolvedRuntimeField resolve(User actor, RuntimeFieldDefinition definition) {
    return extraFields
        .resolve(definition.selector(), actor)
        .orElseThrow(() -> new AssertionError("Unresolved " + definition.selector()));
  }

  @Test
  public void publishesAdHocFieldNamesFoundOnReadableRecords() {
    User owner = createInitAndLoginAnyUser();
    instrumentWith(owner, "Confocal", "Room", "Lab 4", false);
    instrumentWith(owner, "Centrifuge", "Room", "Lab 5", false);

    List<RuntimeFieldDefinition> published = catalog(owner);

    assertEquals(1, published.stream().filter(f -> f.label().equals("Room")).count());
    RuntimeFieldDefinition room = definition(owner, "Room", RuntimeFieldValueType.TEXT);
    assertTrue(room.selector().startsWith("extraFields."));
    assertEquals("", room.sourceId());
  }

  @Test
  public void filtersByTheValueOfAnAdHocField() {
    User owner = createInitAndLoginAnyUser();
    instrumentWith(owner, "Confocal", "Room", "Lab 4", false);
    instrumentWith(owner, "Centrifuge", "Room", "Lab 5", false);
    ResolvedRuntimeField room =
        resolve(owner, definition(owner, "Room", RuntimeFieldValueType.TEXT));

    assertEquals(List.of("Confocal"), matches(owner, room, Operator.EQUAL, "Lab 4"));
    assertEquals(List.of("Centrifuge", "Confocal"), matches(owner, room, Operator.CONTAINS, "Lab"));
  }

  @Test
  public void keepsTwoTypesOfTheSameNameApart() {
    User owner = createInitAndLoginAnyUser();
    instrumentWith(owner, "Text voltage", "Voltage", "high", false);
    instrumentWith(owner, "Number voltage", "Voltage", "240", true);

    RuntimeFieldDefinition asText = definition(owner, "Voltage", RuntimeFieldValueType.TEXT);
    RuntimeFieldDefinition asNumber = definition(owner, "Voltage", RuntimeFieldValueType.NUMBER);
    assertFalse(asText.id().equals(asNumber.id()));

    assertEquals(
        List.of("Text voltage"), matches(owner, resolve(owner, asText), Operator.EQUAL, "high"));
    assertEquals(
        List.of("Number voltage"),
        matches(owner, resolve(owner, asNumber), Operator.GREATER_THAN, 100d));
  }

  @Test
  public void comparesANumericAdHocFieldAsANumber() {
    User owner = createInitAndLoginAnyUser();
    instrumentWith(owner, "Cold", "Temperature", "-80", true);
    instrumentWith(owner, "Warm", "Temperature", "4", true);
    ResolvedRuntimeField temperature =
        resolve(owner, definition(owner, "Temperature", RuntimeFieldValueType.NUMBER));

    assertEquals(List.of("Cold"), matches(owner, temperature, Operator.LESS_THAN, 0d));
  }

  @Test
  public void carriesValuesForAProjectedAdHocField() {
    User owner = createInitAndLoginAnyUser();
    Instrument confocal = instrumentWith(owner, "Confocal", "Room", "Lab 4", false);
    instrumentWith(owner, "Centrifuge", "Other", "x", false);
    RuntimeFieldDefinition room = definition(owner, "Room", RuntimeFieldValueType.TEXT);

    Map<Object, Map<String, Object>> values =
        extraFields.values(List.of(confocal), Set.of(room.id()), owner);

    assertEquals(Map.of(room.id(), "Lab 4"), values.get(confocal.getId()));
  }

  @Test
  public void carriesNamesAndValuesOutsideAscii() {
    User owner = createInitAndLoginAnyUser();
    instrumentWith(owner, "Confocal", "温度計", "摂氏 4 °C", false);
    instrumentWith(owner, "Centrifuge", "温度計", "naïve", false);
    ResolvedRuntimeField sensor =
        resolve(owner, definition(owner, "温度計", RuntimeFieldValueType.TEXT));

    assertEquals(List.of("Confocal"), matches(owner, sensor, Operator.EQUAL, "摂氏 4 °C"));
    assertEquals(List.of("Centrifuge"), matches(owner, sensor, Operator.CONTAINS, "ïve"));
  }

  @Test
  public void carriesNamesContainingQueryPunctuation() {
    User owner = createInitAndLoginAnyUser();
    instrumentWith(owner, "Confocal", "Max voltage (V), peak", "240", false);
    ResolvedRuntimeField voltage =
        resolve(owner, definition(owner, "Max voltage (V), peak", RuntimeFieldValueType.TEXT));

    assertEquals(List.of("Confocal"), matches(owner, voltage, Operator.EQUAL, "240"));
  }

  @Test
  public void hidesFieldNamesOnRecordsTheActorCannotRead() {
    User owner = createInitAndLoginAnyUser();
    instrumentWith(owner, "Private scope", "Secret room", "Lab 9", false);
    User stranger = createInitAndLoginAnyUser();

    assertTrue(catalog(stranger).stream().noneMatch(field -> field.label().equals("Secret room")));
  }

  @Test
  public void hydratesNamedDefinitionsForASavedView() {
    User owner = createInitAndLoginAnyUser();
    instrumentWith(owner, "Confocal", "Room", "Lab 4", false);
    RuntimeFieldDefinition room = definition(owner, "Room", RuntimeFieldValueType.TEXT);

    assertEquals(
        List.of(room.id()),
        extraFields
            .discover(owner, RuntimeFieldCatalogQuery.byIds(Set.of(room.id())))
            .fields()
            .stream()
            .map(RuntimeFieldDefinition::id)
            .toList());
  }
}
