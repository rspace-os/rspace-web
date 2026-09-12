package com.researchspace.dao.hibernate;

import com.blazebit.persistence.CommonQueryBuilder;
import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.dao.query.LikeEscaper;
import com.researchspace.dao.query.LookAheadPagination;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.ExtraFieldIdentity.PublishedType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Reads ad-hoc extra fields against the caller's own read rule for the owning record.
 *
 * <p>The read rule arrives as a {@link FilterExpression} and is recompiled here at this query's own
 * parent alias, which is the same rule the owning collection applies. That is what makes an extra
 * field a narrowing of an already-authorized row set rather than a second, weaker access path.
 *
 * <p>The projections use Blaze's typed constructor queries. The type discriminator remains in the
 * select list so text and number fields with the same name stay distinct.
 */
@Repository("extraFieldDao")
public class ExtraFieldDaoHibernateImpl implements ExtraFieldDao {

  private record DefinitionProjection(String name, Object entityType) {}

  private record ValueProjection(Long parentId, String name, Object entityType, String value) {}

  private static final String FIELD_ALIAS = "extraField";
  private static final String PARENT_ALIAS = "extraFieldParent";
  private static final String NAME = FIELD_ALIAS + ".editInfo.name";
  private static final String VALUE = FIELD_ALIAS + ".editInfo.description";

  @Autowired private CriteriaBuilderFactory criteriaBuilderFactory;
  @Autowired private SessionFactory sessionFactory;

  @Override
  public ExtraFieldPage readableDefinitions(
      ExtraFieldScope scope,
      QueryConstraint constraint,
      String search,
      Set<ExtraFieldRow> wanted,
      Set<FieldType> types,
      int offset,
      int limit) {
    if (types.isEmpty()) {
      return new ExtraFieldPage(List.of(), 0L, false);
    }
    RsqlCollectionQuery.Predicate access = access(scope, constraint);
    boolean hydrating = !wanted.isEmpty();
    CriteriaBuilder<DefinitionProjection> query =
        criteriaBuilderFactory
            .create(sessionFactory.getCurrentSession(), DefinitionProjection.class)
            .from(ExtraField.class, FIELD_ALIAS)
            .selectNew(DefinitionProjection.class)
            .with(NAME)
            .with("TYPE(" + FIELD_ALIAS + ")")
            .end()
            .distinct()
            .whereExpression(common(scope, access))
            .whereExpression(typeRestriction(types));
    if (hydrating) {
      query.whereExpression(NAME + " in :names");
    } else if (search != null) {
      query.whereExpression("lower(" + NAME + ") like :nameSearch escape '!'");
    }
    query.orderByAsc(NAME);
    query.orderByAsc("TYPE(" + FIELD_ALIAS + ")");
    bind(query, access);
    if (hydrating) {
      query.setParameter(
          "names", wanted.stream().map(ExtraFieldRow::name).collect(Collectors.toSet()));
    } else if (search != null) {
      query.setParameter("nameSearch", "%" + LikeEscaper.escape(search) + "%");
    }
    if (!hydrating) {
      query.setFirstResult(offset);
      query.setMaxResults(limit + 1);
    }
    List<ExtraFieldRow> rows = new ArrayList<>(limit + 1);
    for (DefinitionProjection projection : query.getResultList()) {
      FieldType type = typeOf(projection.entityType());
      if (type == null || !types.contains(type)) {
        continue;
      }
      ExtraFieldRow candidate = new ExtraFieldRow(projection.name(), type);
      if (!hydrating || wanted.contains(candidate)) {
        rows.add(candidate);
      }
    }
    LookAheadPagination.Result<ExtraFieldRow> page =
        LookAheadPagination.apply(rows, offset, limit, hydrating);
    return new ExtraFieldPage(page.rows(), page.total(), page.hasMore());
  }

  @Override
  public Map<Long, Map<ExtraFieldRow, String>> valuesByParent(
      ExtraFieldScope scope, Set<Long> parentIds, Set<ExtraFieldRow> definitions) {
    Map<Long, Map<ExtraFieldRow, String>> values = new LinkedHashMap<>();
    if (parentIds.isEmpty() || definitions.isEmpty()) {
      return values;
    }
    String parent = FIELD_ALIAS + "." + scope.parentProperty();
    CriteriaBuilder<ValueProjection> query =
        criteriaBuilderFactory
            .create(sessionFactory.getCurrentSession(), ValueProjection.class)
            .from(ExtraField.class, FIELD_ALIAS)
            .selectNew(ValueProjection.class)
            .with(parent + ".id")
            .with(NAME)
            .with("TYPE(" + FIELD_ALIAS + ")")
            .with(VALUE)
            .end()
            .whereExpression(FIELD_ALIAS + ".deleted = false")
            .whereExpression(parent + ".id in :parentIds")
            .whereExpression(NAME + " in :names")
            .setParameter("parentIds", parentIds);
    query.setParameter(
        "names", definitions.stream().map(ExtraFieldRow::name).collect(Collectors.toSet()));
    for (ValueProjection projection : query.getResultList()) {
      FieldType type = typeOf(projection.entityType());
      if (type == null) {
        continue;
      }
      ExtraFieldRow definition = new ExtraFieldRow(projection.name(), type);
      if (definitions.contains(definition)) {
        values
            .computeIfAbsent(projection.parentId(), ignored -> new LinkedHashMap<>())
            .put(definition, projection.value());
      }
    }
    return values;
  }

  private String common(ExtraFieldScope scope, RsqlCollectionQuery.Predicate access) {
    String parentId = FIELD_ALIAS + "." + scope.parentProperty() + ".id";
    String parentIdProperty =
        scope.parentDescription().requireField(scope.parentDescription().idField()).property();
    StringBuilder hql =
        new StringBuilder(FIELD_ALIAS)
            .append(".deleted = false and ")
            .append(parentId)
            .append(" is not null and exists (select 1 from ")
            .append(scope.parentEntity().getSimpleName())
            .append(" ")
            .append(PARENT_ALIAS)
            .append(" where ")
            .append(PARENT_ALIAS)
            .append(".")
            .append(parentIdProperty)
            .append(" = ")
            .append(parentId);
    if (access != null) {
      hql.append(" and ").append(access.expression());
    }
    return hql.append(")").toString();
  }

  private RsqlCollectionQuery.Predicate access(ExtraFieldScope scope, QueryConstraint constraint) {
    RsqlCollectionQuery.Predicate predicate =
        new RsqlCollectionQuery(scope.parentDescription(), PARENT_ALIAS, "extraFieldAccess")
            .translateTrusted(constraint);
    if (predicate != null && !predicate.subqueries().isEmpty()) {
      throw new IllegalStateException(
          "Read rule requires a subquery, which extra-field discovery cannot nest");
    }
    return predicate;
  }

  private static void bind(CommonQueryBuilder<?> query, RsqlCollectionQuery.Predicate access) {
    if (access != null) {
      access.parameters().forEach(query::setParameter);
    }
  }

  private static Class<?> subtype(FieldType type) {
    PublishedType published = PublishedType.fromFieldType(type);
    return published == null ? ExtraField.class : published.entityType();
  }

  private static String typeRestriction(Set<FieldType> types) {
    return types.stream()
        .sorted()
        .map(type -> "type(" + FIELD_ALIAS + ") = " + subtype(type).getSimpleName())
        .collect(Collectors.joining(" or ", "(", ")"));
  }

  private static FieldType typeOf(Object entityType) {
    for (PublishedType published : PublishedType.values()) {
      if (entityType == published.entityType()) {
        return published.fieldType();
      }
    }
    return null;
  }
}
