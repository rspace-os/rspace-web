package com.researchspace.dao.query;

import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.inventory.model.InventorySharingReadFilters;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.FilterSelector;
import com.researchspace.model.collection.InventoryContainerReadConstraint;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceRoleMembershipConstraint;
import com.researchspace.model.inventory.ContainerLocation;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** Traverses filter and trusted-constraint trees and routes selectors to their storage compiler. */
final class RsqlExpressionCompiler {
  private final CollectionDescription<?> description;
  private final String alias;
  private final RsqlRelationshipCompiler relationships;
  private final RsqlRuntimeFieldCompiler runtimeFields;
  private final RsqlFieldPredicateCompilerRegistry compilers;

  RsqlExpressionCompiler(
      CollectionDescription<?> description,
      String alias,
      RsqlRelationshipCompiler relationships,
      RsqlFieldPredicateCompilerRegistry compilers) {
    this.description = description;
    this.compilers = compilers;
    this.alias = alias;
    this.relationships = relationships;
    this.runtimeFields = new RsqlRuntimeFieldCompiler(description, alias, relationships, compilers);
  }

  String compile(FilterExpression filter, RsqlCompilationState state) {
    if (filter instanceof FilterExpression.Comparison comparison) {
      return compileComparison(comparison, state);
    }
    if (filter instanceof FilterExpression.And and) {
      return compileLogical(and.children(), " AND ", state);
    }
    if (filter instanceof FilterExpression.Or or) {
      return compileLogical(or.children(), " OR ", state);
    }
    throw new IllegalStateException("Unsupported filter expression " + filter.getClass());
  }

  String compileConstraint(QueryConstraint constraint, RsqlCompilationState state) {
    if (constraint instanceof InventoryContainerReadConstraint containers) {
      return compileContainers(containers, state);
    }
    if (constraint instanceof FilterExpression filter) {
      return compile(filter, state);
    }
    if (constraint instanceof QueryConstraint.And and) {
      return compileConstraintLogical(and.children(), " AND ", state);
    }
    if (constraint instanceof QueryConstraint.Or or) {
      return compileConstraintLogical(or.children(), " OR ", state);
    }
    if (constraint instanceof ResourceRoleMembershipConstraint membership) {
      return compileResourceRoleMembership(membership, state);
    }
    throw new IllegalStateException("Unsupported query constraint " + constraint.getClass());
  }

  private String compileContainers(
      InventoryContainerReadConstraint facts, RsqlCompilationState state) {
    if (facts.sysadmin()) {
      return "(" + alias + ".deleted = false)";
    }
    String direct = sharing(facts, facts.visibleOwners(), alias, state);
    List<String> branches = new ArrayList<>();
    branches.add(direct);
    for (String child : List.of("storedContainer", "storedInstrument", "storedSubSample")) {
      String location = state.nextTargetAlias();
      RsqlCompilationState nested = state.nested(false);
      String item = location + "." + child;
      String owner = child.equals("storedSubSample") ? item + ".sample" : item;
      String where =
          location
              + ".container.id = "
              + alias
              + ".id AND "
              + item
              + ".deleted = false AND "
              + sharing(facts, List.of(), owner, nested);
      branches.add("EXISTS " + state.addSubquery(ContainerLocation.class, location, where, nested));
    }
    return "(" + alias + ".deleted = false AND (" + String.join(" OR ", branches) + "))";
  }

  private String sharing(
      InventoryContainerReadConstraint facts,
      List<String> visibleOwners,
      String itemAlias,
      RsqlCompilationState state) {
    var filter =
        InventorySharingReadFilters.readable(
            facts.username(), facts.groupMembers(), facts.groupNames(), visibleOwners);
    var predicate =
        new RsqlCollectionQuery(ApiV2InstrumentResource.DESCRIPTION, itemAlias, compilers)
            .compileTrusted(filter, state.nested(false));
    return state.merge(predicate);
  }

  private String compileResourceRoleMembership(
      ResourceRoleMembershipConstraint membership, RsqlCompilationState state) {
    String assignment = state.nextTargetAlias();
    List<String> applicable = new ArrayList<>();
    applicable.add(
        "("
            + assignment
            + ".user.id = :"
            + state.add(membership.subjectId())
            + " AND "
            + assignment
            + ".user.enabled = true)");
    if (!membership.currentGroupIds().isEmpty()) {
      applicable.add(assignment + ".group.id IN :" + state.add(membership.currentGroupIds()));
    }
    if (membership.includeAllUsers()) {
      applicable.add(assignment + ".audienceKey = :" + state.add(ResourceAudience.ALL_USERS));
    }
    StringBuilder where =
        new StringBuilder(assignment)
            .append(".resourceAccess.id = ")
            .append(alias)
            .append(".")
            .append(membership.resourceAccessIdPath())
            .append(" AND ")
            .append(assignment)
            .append(".roleKey IN :")
            .append(state.add(membership.readableRoleKeys()))
            .append(" AND (")
            .append(String.join(" OR ", applicable))
            .append(")");
    return "EXISTS "
        + state.addSubquery(
            new RsqlCollectionQuery.Subquery(
                ResourceRoleAssignment.class, assignment, where.toString()));
  }

  private String compileConstraintLogical(
      List<QueryConstraint> children, String separator, RsqlCompilationState state) {
    if (children.isEmpty()) {
      throw new NoSuchElementException();
    }
    return "("
        + children.stream()
            .map(child -> compileConstraint(child, state))
            .collect(Collectors.joining(separator))
        + ")";
  }

  private String compileLogical(
      List<FilterExpression> children, String separator, RsqlCompilationState state) {
    return "("
        + children.stream()
            .map(child -> compile(child, state))
            .collect(Collectors.joining(separator))
        + ")";
  }

  private String compileComparison(
      FilterExpression.Comparison comparison, RsqlCompilationState state) {
    ResolvedRuntimeField runtimeField = state.runtime.find(comparison.field());
    if (runtimeField != null) {
      String relationship = state.runtime.relationshipFor(comparison.field());
      return relationship == null
          ? runtimeFields.compileRuntimeField(comparison, runtimeField, state)
          : runtimeFields.compileRuntimeFieldThroughRelationship(
              comparison, runtimeField, relationship, state);
    }
    FilterSelector<?> selector = description.findFilterSelector(comparison.field()).orElse(null);
    if (selector == null) {
      return relationships.compileRelationshipField(comparison, state);
    }
    Operator operator = comparison.operator();
    if (!selector.operators().contains(operator)) {
      throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
    }
    if (selector instanceof FilterSelector.RelationshipPart<?> relationship) {
      return relationships.compileRelationship(comparison, relationship, state);
    }
    if (selector instanceof FilterSelector.Property<?> property) {
      return compileProperty(comparison, property, state);
    }
    if (selector instanceof FilterSelector.RuntimeField<?> runtime) {
      return runtimeFields.compileRuntimeField(comparison, runtime.field(), state);
    }
    throw new IllegalStateException("Unsupported filter selector " + selector.getClass());
  }

  private String compileProperty(
      FilterExpression.Comparison comparison,
      FilterSelector.Property<?> property,
      RsqlCompilationState state) {
    compilers.validate(property.type());
    Operator operator = comparison.operator();
    String path = alias + "." + property.property();
    if (operator == Operator.EXISTS) {
      return path + (Boolean.TRUE.equals(comparison.values().get(0)) ? " IS NOT NULL" : " IS NULL");
    }
    return compilers.require(property.type().comparisonStrategy()).compile(comparison, path, state);
  }
}
