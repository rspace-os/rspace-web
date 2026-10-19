package com.researchspace.dao.hibernate;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.dao.query.LikeEscaper;
import com.researchspace.dao.query.LookAheadPagination;
import com.researchspace.dao.query.LookAheadPagination.Result;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.ExtraFieldIdentity;
import com.researchspace.model.inventory.field.ExtraFieldIdentity.Definition;
import com.researchspace.model.inventory.field.ExtraFieldIdentity.PublishedType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

/**
 * Reads ad-hoc extra fields against the caller's own read rule for the owning record.
 *
 * <p>The read rule arrives as a {@link QueryConstraint} and is recompiled here at this query's own
 * parent alias, which is the same rule the owning collection applies. That is what makes an extra
 * field a narrowing of an already-authorized row set rather than a second, weaker access path.
 *
 * <p>The projections use Blaze's typed constructor queries. The type discriminator remains in the
 * select list so text and number fields with the same name stay distinct.
 */
@Repository("extraFieldDao")
public class ExtraFieldDaoHibernateImpl implements ExtraFieldDao {

  /** The hex identity remains selected so SQL DISTINCT preserves exact names under DB collation. */
  public record DefinitionProjection(String name, Object entityType, String nameIdentity) {}

  public record ValueProjection(Long parentId, String name, Object entityType, String value) {}

  private static final String FIELD_ALIAS = "extraField";
  private static final String PARENT_ALIAS = "extraFieldParent";
  private static final String NAME = FIELD_ALIAS + ".editInfo.name";
  private static final String NAME_IDENTITY = "LOWER(FUNCTION('HEX', " + NAME + "))";
  private static final String VALUE = FIELD_ALIAS + ".editInfo.description";

  private final CriteriaBuilderFactory criteriaBuilderFactory;
  private final SessionFactory sessionFactory;

  public ExtraFieldDaoHibernateImpl(
      CriteriaBuilderFactory criteriaBuilderFactory, SessionFactory sessionFactory) {
    this.criteriaBuilderFactory = criteriaBuilderFactory;
    this.sessionFactory = sessionFactory;
  }

  @Override
  public Result<Definition> readableDefinitions(
      ExtraFieldScope scope,
      QueryConstraint constraint,
      String search,
      Set<Definition> wanted,
      Set<FieldType> types,
      int offset,
      int limit) {
    if (types.isEmpty()) {
      return new Result<>(List.of(), 0L, false);
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
            .with(NAME_IDENTITY)
            .end()
            .distinct()
            .whereExpression(FIELD_ALIAS + ".deleted = false")
            .whereExpression(typeRestriction(types));
    String parentIdProperty =
        scope.parentDescription().requireField(scope.parentDescription().idField()).property();
    var parent =
        query
            .whereExists()
            .from(scope.parentEntity(), PARENT_ALIAS)
            .select("1")
            .where(PARENT_ALIAS + "." + parentIdProperty)
            .eqExpression(FIELD_ALIAS + "." + scope.parentProperty() + ".id");
    if (access != null) {
      access.applyTo(parent, query);
    }
    parent.end();
    if (hydrating) {
      query.whereExpression(NAME + " in :names");
      query.whereExpression(NAME_IDENTITY + " in :nameIdentities");
    } else if (search != null) {
      query.whereExpression("lower(" + NAME + ") like :nameSearch escape '!'");
    }
    query.orderByAsc(NAME_IDENTITY);
    query.orderByAsc("TYPE(" + FIELD_ALIAS + ")");
    if (hydrating) {
      query.setParameter(
          "names", wanted.stream().map(Definition::name).collect(Collectors.toSet()));
      query.setParameter(
          "nameIdentities",
          wanted.stream()
              .map(Definition::name)
              .map(ExtraFieldIdentity::encodeName)
              .collect(Collectors.toSet()));
    } else if (search != null) {
      query.setParameter("nameSearch", "%" + LikeEscaper.escape(search) + "%");
    }
    if (!hydrating) {
      query.setFirstResult(offset);
      query.setMaxResults(limit + 1);
    }
    List<Definition> rows = new ArrayList<>(limit + 1);
    for (DefinitionProjection projection : query.getResultList()) {
      PublishedType type = publishedType(projection.entityType());
      if (type == null) {
        continue;
      }
      Definition candidate = new Definition(projection.name(), type.fieldType());
      if (!hydrating || wanted.contains(candidate)) {
        rows.add(candidate);
      }
    }
    return LookAheadPagination.apply(rows, offset, limit, hydrating);
  }

  @Override
  public Map<Long, Map<Definition, String>> valuesByParent(
      ExtraFieldScope scope, Set<Long> parentIds, Set<Definition> definitions) {
    Map<Long, Map<Definition, String>> values = new LinkedHashMap<>();
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
            .whereExpression(NAME_IDENTITY + " in :nameIdentities")
            .setParameter("parentIds", parentIds);
    query.setParameter(
        "nameIdentities",
        definitions.stream()
            .map(Definition::name)
            .map(ExtraFieldIdentity::encodeName)
            .collect(Collectors.toSet()));
    for (ValueProjection projection : query.getResultList()) {
      PublishedType type = publishedType(projection.entityType());
      if (type == null) {
        continue;
      }
      Definition definition = new Definition(projection.name(), type.fieldType());
      if (definitions.contains(definition)) {
        values
            .computeIfAbsent(projection.parentId(), ignored -> new LinkedHashMap<>())
            .put(definition, projection.value());
      }
    }
    return values;
  }

  private RsqlCollectionQuery.Predicate access(ExtraFieldScope scope, QueryConstraint constraint) {
    return new RsqlCollectionQuery(scope.parentDescription(), PARENT_ALIAS, "extraFieldAccess")
        .translateTrusted(constraint);
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

  private static PublishedType publishedType(Object entityType) {
    return entityType instanceof Class<?> type ? PublishedType.fromEntityType(type) : null;
  }
}
