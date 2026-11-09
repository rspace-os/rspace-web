package com.researchspace.booking.api.v2;

import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.inventory.InventoryRecord;

final class BookingDocumentValues {

  private BookingDocumentValues() {}

  static ResolvedBookableTarget target(
      ParsedDocument document, CollectionDescription<?> description) {
    Object value = document.values().get("target");
    if (value == null) {
      return null;
    }
    ResolvedResourceReference<?, ?> resolved = ResolvedResourceReference.class.cast(value);
    ResourceReference<?, ?> reference = resolved.reference();
    BookableTargetType type = BookableTargetType.class.cast(reference.kind());
    Long id = Long.class.cast(reference.id());
    RelationshipTarget<?> metadata = description.requireRelationship("target").targetForKind(type);
    InventoryRecord entity = InventoryRecord.class.cast(resolved.entityAs(metadata.entityType()));
    return new ResolvedBookableTarget(new BookableTargetReference(type, id), entity);
  }

  static <T> T value(ParsedDocument document, String field, Class<T> type) {
    return type.cast(document.values().get(field));
  }
}
