package com.researchspace.dao;

import com.researchspace.dao.query.LookAheadPagination.Result;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.ExtraFieldIdentity.Definition;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the ad-hoc extra fields reachable through one kind of inventory record, and their values.
 *
 * <p>An extra field belongs to a single record and has no template behind it, so there is no
 * definition row to read: a "definition" here is the set of extra fields sharing a name and a
 * declared type, discovered by looking at the values themselves.
 *
 * <p>Every method takes the caller's read constraint for the owning record rather than a user, so
 * the row rule and the field rule are enforced in one database query, exactly as the
 * template-backed reader does.
 */
public interface ExtraFieldDao {

  /**
   * Which records these extra fields hang off, and how to reach them.
   *
   * @param parentEntity the persistent entity owning the fields, such as {@code Instrument}
   * @param parentDescription that collection's description, for recompiling its read rule
   * @param parentProperty the {@code ExtraField} property pointing at it, such as {@code
   *     instrumentEntity}
   */
  record ExtraFieldScope(
      Class<?> parentEntity, CollectionDescription<?> parentDescription, String parentProperty) {

    public ExtraFieldScope {
      Objects.requireNonNull(parentEntity, "Parent entity");
      Objects.requireNonNull(parentDescription, "Parent collection description");
      if (parentProperty == null || parentProperty.isBlank()) {
        throw new IllegalArgumentException("Parent property must not be blank");
      }
    }
  }

  /**
   * Definitions this caller may name, narrowed and bounded.
   *
   * @param constraint the caller's read rule for the owning record, or null
   * @param search lower-case substring of the field name, or null
   * @param wanted specific definitions to hydrate; when non-empty, search and paging are ignored
   * @param types the declared types this API publishes
   */
  Result<Definition> readableDefinitions(
      ExtraFieldScope scope,
      QueryConstraint constraint,
      String search,
      Set<Definition> wanted,
      Set<FieldType> types,
      int offset,
      int limit);

  /**
   * Values of the selected definitions for the selected records, in one query.
   *
   * <p>The outer key is the record ID. A definition that does not apply to a record is absent
   * rather than null, which is the documented difference between "not applicable" and "applicable
   * but empty".
   */
  Map<Long, Map<Definition, String>> valuesByParent(
      ExtraFieldScope scope, Set<Long> parentIds, Set<Definition> definitions);
}
