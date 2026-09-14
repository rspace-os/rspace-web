package com.researchspace.search.customfield;

import com.researchspace.model.collection.RuntimeFieldNamespaces;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.ExtraFieldIdentity;
import com.researchspace.model.inventory.field.InventoryEntityField;
import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.mapper.pojo.bridge.TypeBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.TypeBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.TypeBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.TypeBridgeWriteContext;

/**
 * Indexes each of an instrument's runtime-field values under its own field.
 *
 * <p>Both namespaces an instrument publishes: template-backed custom fields, keyed by the template
 * field's ID, and ad-hoc extra fields, keyed by their (name, type) identity. They share one field
 * template because they are the same shape to a reader — "this definition's value on this item" —
 * and differ only in who issues the ID.
 *
 * <p>Bound from {@code @TypeBinding} on {@link Instrument}, the same way {@code
 * TemporaryDocRoutingBinder} is bound from {@code StructuredDocument}.
 *
 * <p>Field names cannot be known when the schema is built — they depend on which templates exist
 * and on what users have typed — so the schema declares a template and the bridge writes names
 * matching it.
 */
public class InstrumentRuntimeFieldBinder implements TypeBinder {

  @Override
  public void bind(TypeBindingContext context) {
    context
        .dependencies()
        .use("fields.data")
        .use("fields.deleted")
        .use("extraFields.editInfo.name")
        .use("extraFields.editInfo.description")
        .use("extraFields.deleted");

    IndexSchemaElement schema = context.indexSchemaElement();
    schema
        .fieldTemplate(
            RuntimeFieldIndexNames.VALUE_TEMPLATE,
            context.typeFactory().asString().analyzer("axiopeanalyzer"))
        .matchingPathGlob(RuntimeFieldIndexNames.VALUE_GLOB);

    context.bridge(Instrument.class, new Bridge());
  }

  /** Writes one value per definition, skipping deleted copies and empty values. */
  private static final class Bridge implements TypeBridge<Instrument> {

    @Override
    public void write(
        DocumentElement target, Instrument instrument, TypeBridgeWriteContext context) {
      writeCustomFields(target, instrument);
      writeExtraFields(target, instrument);
    }

    private void writeCustomFields(DocumentElement target, Instrument instrument) {
      if (instrument.getFields() == null) {
        return;
      }
      for (InventoryEntityField field : instrument.getFields()) {
        if (field == null || field.isDeleted() || field.getTemplateField() == null) {
          continue;
        }
        write(
            target,
            RuntimeFieldNamespaces.CUSTOM_FIELDS,
            field.getTemplateField().getOid().getIdString(),
            field.getData());
      }
    }

    private void writeExtraFields(DocumentElement target, Instrument instrument) {
      if (instrument.getExtraFields() == null) {
        return;
      }
      for (ExtraField field : instrument.getExtraFields()) {
        if (field == null || field.isDeleted()) {
          continue;
        }
        write(
            target,
            RuntimeFieldNamespaces.EXTRA_FIELDS,
            ExtraFieldIdentity.encode(field.getName(), field.getType()),
            field.getData());
      }
    }

    private void write(
        DocumentElement target, String namespace, String definitionId, String value) {
      if (definitionId == null || value == null || value.isEmpty()) {
        return;
      }
      String field = RuntimeFieldIndexNames.valueField(namespace, definitionId);
      if (field != null) {
        target.addValue(field, value);
      }
    }
  }
}
