package com.researchspace.dao;

import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the template field definitions reachable through instruments, and their values.
 *
 * <p>Every method takes the caller's instrument read constraint rather than a user, so the row rule
 * and the definition rule are enforced in one database query. Resolving readable instruments first
 * and then filtering in Java would put an unbounded ID list in the second query and would let the
 * two rules drift apart.
 */
public interface InstrumentCustomFieldDao {

  /**
   * One definition, as columns rather than as an entity.
   *
   * <p>A projection because loading the entity also drags in its owning template and that
   * template's four lazy collections, once per definition. At catalog scale that is thousands of
   * statements to produce six values per row.
   */
  record DefinitionRow(
      Long id,
      String name,
      FieldType type,
      Integer columnIndex,
      Long sourceId,
      String sourceName) {}

  /**
   * One page of the definitions used by at least one instrument the caller may read.
   *
   * @param total exact count, or null when the page stopped short of the end and only {@code
   *     hasMore} is known
   */
  record DefinitionPage(List<DefinitionRow> rows, Long total, boolean hasMore) {}

  /**
   * Definitions this caller may name, narrowed and bounded.
   *
   * @param constraint the caller's instrument read rule, compiled against the instrument alias
   * @param search lower-case substring of the definition name, or null
   * @param ids specific definition IDs; when non-empty, search and paging are ignored
   * @param types the declared types this API publishes. Applied in the query rather than by the
   *     caller so that a page holds the number of rows it asked for and the total counts only rows
   *     the caller can be shown.
   */
  DefinitionPage readableDefinitions(
      QueryConstraint constraint,
      String search,
      Set<Long> ids,
      Set<FieldType> types,
      int offset,
      int limit);

  Map<Long, List<String>> optionsFor(Set<Long> definitionIds);

  /**
   * Values of the selected definitions for the selected instruments, in one query.
   *
   * <p>The outer key is the instrument ID and the inner key is the definition ID. A definition that
   * does not apply to an instrument is absent rather than null, which is the documented difference
   * between "not applicable" and "applicable but empty".
   */
  Map<Long, Map<Long, InventoryEntityField>> valuesByInstrument(
      Set<Long> instrumentIds, Set<Long> definitionIds);

  /**
   * Which of {@code instrumentIds} the caller's read rule admits.
   *
   * <p>Needed because {@link #valuesByInstrument} trusts its caller, which is right when the
   * instruments are the page the caller just read and wrong when they were reached through another
   * collection's relationship. A bookable item names an instrument; being able to read the booking
   * configuration says nothing about being able to read that instrument, so the rule is applied
   * here rather than assumed.
   */
  Set<Long> readableInstruments(QueryConstraint constraint, Set<Long> instrumentIds);
}
