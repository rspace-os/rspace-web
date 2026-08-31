package com.researchspace.dao.hibernate;

import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.ExtraFieldIdentity;
import jakarta.persistence.Query;
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
 * Reads ad-hoc extra fields against the caller's own read rule for the owning record.
 *
 * <p>The read rule arrives as a {@link FilterExpression} and is recompiled here at this query's own
 * parent alias, which is the same rule the owning collection applies. That is what makes an extra
 * field a narrowing of an already-authorized row set rather than a second, weaker access path.
 *
 * <p>Plain HQL rather than the criteria builder, because these queries need {@code type(f)} to tell
 * a text field from a number field of the same name, and the value column is reached through an
 * embeddable. The compiled read rule is still parameterized: only its expression text, which is
 * built from server-owned property names, is concatenated.
 */
@Repository("extraFieldDao")
public class ExtraFieldDaoHibernateImpl implements ExtraFieldDao {

  private static final String FIELD_ALIAS = "extraField";
  private static final String PARENT_ALIAS = "extraFieldParent";
  private static final String NAME = FIELD_ALIAS + ".editInfo.name";
  private static final String VALUE = FIELD_ALIAS + ".editInfo.description";

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
    StringBuilder hql =
        new StringBuilder("select distinct ")
            .append(NAME)
            .append(", type(")
            .append(FIELD_ALIAS)
            .append(") from ExtraField ")
            .append(FIELD_ALIAS)
            .append(" where ")
            .append(common(scope, access))
            .append(" and char_length(")
            .append(NAME)
            .append(") <= :maximumNameLength");
    if (hydrating) {
      hql.append(" and ").append(NAME).append(" in :names");
    } else if (search != null) {
      hql.append(" and lower(").append(NAME).append(") like :nameSearch escape '!'");
    }
    hql.append(" order by ").append(NAME).append(" asc, type(").append(FIELD_ALIAS).append(") asc");
    Query query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Object[].class);
    bind(query, access);
    query.setParameter("maximumNameLength", ExtraFieldIdentity.MAX_NAME_LENGTH);
    if (hydrating) {
      query.setParameter(
          "names",
          wanted.stream().map(ExtraFieldRow::name).collect(java.util.stream.Collectors.toSet()));
    } else if (search != null) {
      query.setParameter("nameSearch", "%" + escapeLike(search) + "%");
      query.setFirstResult(offset);
      query.setMaxResults(limit + 1);
    } else {
      query.setFirstResult(offset);
      query.setMaxResults(limit + 1);
    }
    List<ExtraFieldRow> rows = new ArrayList<>(limit + 1);
    for (Object row : query.getResultList()) {
      Object[] columns = (Object[]) row;
      FieldType type = typeOf(columns[1]);
      if (type == null || !types.contains(type)) {
        continue;
      }
      ExtraFieldRow candidate = new ExtraFieldRow((String) columns[0], type);
      if (!hydrating || wanted.contains(candidate)) {
        rows.add(candidate);
      }
    }
    boolean hasMore = !hydrating && rows.size() > limit;
    if (hasMore) {
      rows.remove(rows.size() - 1);
    }
    boolean exact = hydrating || !rows.isEmpty() || offset == 0;
    return new ExtraFieldPage(
        rows, hasMore || !exact ? null : (long) offset + rows.size(), hasMore);
  }

  @Override
  public Map<Long, Map<ExtraFieldRow, String>> valuesByParent(
      ExtraFieldScope scope, Set<Long> parentIds, Set<ExtraFieldRow> definitions) {
    Map<Long, Map<ExtraFieldRow, String>> values = new LinkedHashMap<>();
    if (parentIds.isEmpty() || definitions.isEmpty()) {
      return values;
    }
    String hql =
        "select "
            + FIELD_ALIAS
            + "."
            + scope.parentProperty()
            + ".id, "
            + NAME
            + ", type("
            + FIELD_ALIAS
            + "), "
            + VALUE
            + " from ExtraField "
            + FIELD_ALIAS
            + " where "
            + FIELD_ALIAS
            + ".deleted = false and "
            + FIELD_ALIAS
            + "."
            + scope.parentProperty()
            + ".id in :parentIds and "
            + NAME
            + " in :names";
    Query query = sessionFactory.getCurrentSession().createQuery(hql, Object[].class);
    query.setParameter("parentIds", parentIds);
    query.setParameter(
        "names",
        definitions.stream().map(ExtraFieldRow::name).collect(java.util.stream.Collectors.toSet()));
    for (Object row : query.getResultList()) {
      Object[] columns = (Object[]) row;
      FieldType type = typeOf(columns[2]);
      if (type == null) {
        continue;
      }
      ExtraFieldRow definition = new ExtraFieldRow((String) columns[1], type);
      if (definitions.contains(definition)) {
        values
            .computeIfAbsent((Long) columns[0], ignored -> new LinkedHashMap<>())
            .put(definition, (String) columns[3]);
      }
    }
    return values;
  }

  private String common(ExtraFieldScope scope, RsqlCollectionQuery.Predicate access) {
    String parentId = FIELD_ALIAS + "." + scope.parentProperty() + ".id";
    return FIELD_ALIAS
        + ".deleted = false and "
        + parentId
        + " is not null and exists (select 1 from "
        + scope.parentEntity().getSimpleName()
        + " "
        + PARENT_ALIAS
        + " where "
        + PARENT_ALIAS
        + "."
        + scope.parentDescription().requireField(scope.parentDescription().idField()).property()
        + " = "
        + parentId
        + (access == null ? "" : " and " + access.expression())
        + ")";
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

  private static void bind(Query query, RsqlCollectionQuery.Predicate access) {
    if (access != null) {
      access.parameters().forEach(query::setParameter);
    }
  }

  private static Class<?> subtype(FieldType type) {
    return switch (type) {
      case TEXT -> com.researchspace.model.inventory.field.ExtraTextField.class;
      case NUMBER -> com.researchspace.model.inventory.field.ExtraNumberField.class;
      case LINK -> com.researchspace.model.inventory.field.ExtraLinkField.class;
      default -> ExtraField.class;
    };
  }

  private static FieldType typeOf(Object entityType) {
    if (entityType == com.researchspace.model.inventory.field.ExtraTextField.class) {
      return FieldType.TEXT;
    }
    if (entityType == com.researchspace.model.inventory.field.ExtraNumberField.class) {
      return FieldType.NUMBER;
    }
    if (entityType == com.researchspace.model.inventory.field.ExtraLinkField.class) {
      return FieldType.LINK;
    }
    return null;
  }

  private static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  static Set<String> names(Set<ExtraFieldRow> rows) {
    Set<String> names = new LinkedHashSet<>();
    rows.forEach(row -> names.add(row.name()));
    return names;
  }
}
