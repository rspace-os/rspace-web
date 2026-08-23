package com.researchspace.dao.hibernate;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.InstrumentCustomFieldDao;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Reads instrument custom-field definitions and values against the caller's own read rule.
 *
 * <p>The read rule arrives as a {@link FilterExpression} and is recompiled here at this query's own
 * instrument alias, which is the same rule the instrument list applies. That is what makes a custom
 * field a narrowing of an already-authorized row set rather than a second, weaker access path.
 */
@Repository("instrumentCustomFieldDao")
public class InstrumentCustomFieldDaoHibernateImpl implements InstrumentCustomFieldDao {

  private static final String DEFINITION_ALIAS = "customFieldDef";
  private static final String COPY_ALIAS = "customFieldCopy";
  private static final String INSTRUMENT_ALIAS = "customFieldInstrument";
  private static final String SOURCE_ALIAS = "customFieldSource";

  @Autowired private CriteriaBuilderFactory criteriaBuilderFactory;
  @Autowired private SessionFactory sessionFactory;

  @Override
  public DefinitionPage readableDefinitions(
      FilterExpression constraint,
      String search,
      Set<Long> ids,
      Set<FieldType> types,
      int offset,
      int limit) {
    if (types.isEmpty()) {
      return new DefinitionPage(List.of(), 0L, false);
    }
    RsqlCollectionQuery.Predicate predicate =
        new RsqlCollectionQuery(
                ApiV2InstrumentResource.DESCRIPTION, INSTRUMENT_ALIAS, "customFieldAccess")
            .translateTrusted(constraint);
    if (predicate != null && !predicate.subqueries().isEmpty()) {
      throw new IllegalStateException(
          "Instrument read rule requires a subquery, which custom-field discovery cannot nest");
    }
    String source = SOURCE_ALIAS;
    CriteriaBuilder<Tuple> query =
        criteriaBuilderFactory
            .create(sessionFactory.getCurrentSession(), Tuple.class)
            .from(InventoryEntityField.class, DEFINITION_ALIAS)
            .innerJoin(DEFINITION_ALIAS + ".instrumentEntity", SOURCE_ALIAS)
            .select(DEFINITION_ALIAS + ".id")
            .select(DEFINITION_ALIAS + ".name")
            .select(DEFINITION_ALIAS + ".type")
            .select(DEFINITION_ALIAS + ".columnIndex")
            .select(source + ".id")
            .select(source + ".editInfo.name");
    restrict(query, predicate, search, ids, types);
    query.orderByAsc(DEFINITION_ALIAS + ".id");
    boolean hydrating = !ids.isEmpty();
    if (!hydrating) {
      query.setFirstResult(offset).setMaxResults(limit + 1);
    }
    List<DefinitionRow> rows = new ArrayList<>(limit + 1);
    for (Tuple row : query.getResultList()) {
      rows.add(
          new DefinitionRow(
              (Long) row.get(0),
              (String) row.get(1),
              (FieldType) row.get(2),
              (Integer) row.get(3),
              (Long) row.get(4),
              (String) row.get(5)));
    }
    boolean hasMore = !hydrating && rows.size() > limit;
    if (hasMore) {
      rows.remove(rows.size() - 1);
    }
    boolean exact = hydrating || !rows.isEmpty() || offset == 0;
    Long total = hasMore || !exact ? null : (long) offset + rows.size();
    return new DefinitionPage(rows, total, hasMore);
  }

  private void restrict(
      CriteriaBuilder<?> query,
      RsqlCollectionQuery.Predicate predicate,
      String search,
      Set<Long> ids,
      Set<FieldType> types) {
    query.whereExpression(DEFINITION_ALIAS + ".type IN :publishedTypes");
    query.setParameter("publishedTypes", types);
    query.whereExpression(DEFINITION_ALIAS + ".templateField IS NULL");
    String correlation =
        COPY_ALIAS
            + ".templateField.id = "
            + DEFINITION_ALIAS
            + ".id"
            + " AND "
            + COPY_ALIAS
            + ".deleted = false"
            + " AND "
            + COPY_ALIAS
            + ".instrumentEntity.id = "
            + INSTRUMENT_ALIAS
            + ".id";
    query
        .whereExists()
        .from(InventoryEntityField.class, COPY_ALIAS)
        .from(Instrument.class, INSTRUMENT_ALIAS)
        .select("1")
        .whereExpression(
            predicate == null ? correlation : correlation + " AND " + predicate.expression())
        .end();
    if (!ids.isEmpty()) {
      query.whereExpression(DEFINITION_ALIAS + ".id IN :requestedIds");
      query.setParameter("requestedIds", ids);
    } else if (search != null) {
      query.whereExpression("LOWER(" + DEFINITION_ALIAS + ".name) LIKE :nameSearch ESCAPE '!'");
      query.setParameter("nameSearch", "%" + escapeLike(search) + "%");
    }
    if (predicate != null) {
      predicate.parameters().forEach(query::setParameter);
    }
  }

  @Override
  public Set<Long> readableInstruments(FilterExpression constraint, Set<Long> instrumentIds) {
    if (instrumentIds.isEmpty()) {
      return Set.of();
    }
    RsqlCollectionQuery.Predicate predicate =
        new RsqlCollectionQuery(
                ApiV2InstrumentResource.DESCRIPTION, INSTRUMENT_ALIAS, "customFieldRowAccess")
            .translateTrusted(constraint);
    if (predicate != null && !predicate.subqueries().isEmpty()) {
      throw new IllegalStateException(
          "Instrument read rule requires a subquery, which custom-field projection cannot nest");
    }
    CriteriaBuilder<Long> query =
        criteriaBuilderFactory
            .create(sessionFactory.getCurrentSession(), Long.class)
            .from(Instrument.class, INSTRUMENT_ALIAS)
            .select(INSTRUMENT_ALIAS + ".id");
    query.whereExpression(INSTRUMENT_ALIAS + ".id IN :instrumentIds");
    query.setParameter("instrumentIds", instrumentIds);
    if (predicate != null) {
      query.whereExpression(predicate.expression());
      predicate.parameters().forEach(query::setParameter);
    }
    return new LinkedHashSet<>(query.getResultList());
  }

  @Override
  public Map<Long, List<String>> optionsFor(Set<Long> definitionIds) {
    Map<Long, List<String>> options = new LinkedHashMap<>();
    if (definitionIds.isEmpty()) {
      return options;
    }
    collectOptions(
        options,
        "select f.id, f.choiceDef.choiceOptions from "
            + InventoryChoiceField.class.getSimpleName()
            + " f where f.id in :ids",
        definitionIds);
    collectOptions(
        options,
        "select f.id, f.radioDef.radioOptions from "
            + InventoryRadioField.class.getSimpleName()
            + " f where f.id in :ids",
        definitionIds);
    return options;
  }

  private void collectOptions(Map<Long, List<String>> options, String hql, Set<Long> ids) {
    List<Object[]> rows =
        sessionFactory
            .getCurrentSession()
            .createQuery(hql, Object[].class)
            .setParameterList("ids", ids)
            .list();
    for (Object[] row : rows) {
      Object stored = row[1];
      Object parsed =
          stored == null ? null : RuntimeFieldValueType.CHOICE.serialize((String) stored);
      options.put((Long) row[0], asStrings(parsed));
    }
  }

  @SuppressWarnings("unchecked")
  private static List<String> asStrings(Object parsed) {
    return parsed instanceof List ? List.copyOf((List<String>) parsed) : List.of();
  }

  private static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  @Override
  public Map<Long, Map<Long, InventoryEntityField>> valuesByInstrument(
      Set<Long> instrumentIds, Set<Long> definitionIds) {
    Map<Long, Map<Long, InventoryEntityField>> values = new LinkedHashMap<>();
    if (instrumentIds.isEmpty() || definitionIds.isEmpty()) {
      return values;
    }
    List<InventoryEntityField> rows =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from InventoryEntityField f"
                    + " where f.instrumentEntity.id in :instrumentIds"
                    + " and f.templateField.id in :definitionIds"
                    + " and f.deleted = false",
                InventoryEntityField.class)
            .setParameterList("instrumentIds", instrumentIds)
            .setParameterList("definitionIds", definitionIds)
            .list();
    for (InventoryEntityField row : rows) {
      values
          .computeIfAbsent(row.getInstrumentEntity().getId(), ignored -> new LinkedHashMap<>())
          .put(row.getTemplateField().getId(), row);
    }
    return values;
  }
}
