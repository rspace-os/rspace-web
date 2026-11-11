package com.researchspace.dao;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InstrumentCustomFieldTestUtils {

  private InstrumentCustomFieldTestUtils() {}

  static Instrument instrumentFromTemplate(
      InstrumentDao instrumentDao,
      InstrumentEntityApiManager instrumentApiManager,
      User owner,
      ApiInstrumentTemplate template,
      String name,
      String value) {
    ApiInstrument request = new ApiInstrument();
    request.setName(name);
    request.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiManager.createNewApiInstrument(request, owner);
    Instrument instrument = instrumentDao.get(created.getId());
    for (InventoryEntityField field : instrument.getActiveFields()) {
      field.setFieldData(value);
    }
    instrumentDao.save(instrument);
    return instrument;
  }

  static ResolvedRuntimeField definitionOf(
      InstrumentCustomFieldManager customFields, User actor, ApiInstrumentTemplate template) {
    String templateId = String.valueOf(template.getId());
    return customFields
        .discover(
            actor,
            new RuntimeFieldCatalogQuery(null, Set.of(), 1, RuntimeFieldCatalogQuery.MAX_LIMIT))
        .fields()
        .stream()
        .filter(field -> field.sourceId().endsWith(templateId))
        .findFirst()
        .flatMap(field -> customFields.resolve(field.selector(), actor))
        .orElseThrow(() -> new AssertionError("No readable definition for template " + templateId));
  }

  static ResourceRequest filter(ResolvedRuntimeField field, Operator operator, Object value) {
    return new ResourceRequest(
        new FilterExpression.Comparison(field.selector(), operator, List.of(value), false),
        null,
        List.of(new Sort("name", true), new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        ResourceFieldSelections.root(FieldSelection.all()),
        IncludeTree.empty(),
        new RuntimeFieldSelection(Map.of(field.selector(), field), Set.of()));
  }

  static List<String> matches(
      InstrumentDao instrumentDao,
      InstrumentReadAccess instrumentReadAccess,
      ResourceRequest request,
      User actor) {
    return instrumentDao
        .getReadableResources(
            request,
            instrumentReadAccess.check(new AccessContext(actor, Operation.READ, "instruments")))
        .resources()
        .stream()
        .map(Instrument::getName)
        .toList();
  }
}
