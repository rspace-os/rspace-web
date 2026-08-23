package com.researchspace.api.v2.resource;

import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.model.ApiV2AuditQuery;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.core.util.DateRangeRestrictor;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import com.researchspace.service.audit.search.AuditTrailHandler;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import com.researchspace.service.audit.search.IAuditTrailSearchConfig;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Searches the existing audit trail for one readable REST API v2 resource. */
@Component
public final class ApiV2AuditLog {

  private static final Logger LOG = LoggerFactory.getLogger(ApiV2AuditLog.class);
  static final Duration MAX_SEARCH_RANGE = Duration.ofDays(183);

  private final AuditTrailHandler auditTrail;
  private final DateRangeRestrictor dateRangeRestrictor = new DateRangeRestrictor();
  private final Map<Class<?>, Optional<AuditMetadata>> metadata = new ConcurrentHashMap<>();

  public ApiV2AuditLog(AuditTrailHandler auditTrail) {
    this.auditTrail = auditTrail;
  }

  /** Returns an audit page after resource and audit-actor access checks. */
  public ApiV2ListResult<ApiV2AuditEvent> search(
      ApiV2ResourceRegistration<?, ?> resource, String rawId, ApiV2AuditQuery query, User actor) {
    if (actor == null) {
      throw new ApiV2AuthenticationException();
    }
    ResolvedTarget target = resource.requireReadableForAudit(rawId, actor);
    Optional<AuditTarget> auditTarget = auditTarget(resource, target);
    if (auditTarget.isEmpty()) {
      return ApiV2ListResult.of(List.of(), 0, query.getLimit(), query.getPage());
    }

    requireSearchableRange(query);
    // Still called, so an open-ended request gets the default window rather than scanning forever.
    // A range the client did supply has already been rejected above if it was too wide.
    dateRangeRestrictor.restrictDateRange(query, MAX_SEARCH_RANGE);
    PaginationCriteria<AuditTrailSearchResult> pagination =
        PaginationCriteria.createDefaultForClass(AuditTrailSearchResult.class);
    pagination.setPageNumber((long) query.getPage() - 1);
    pagination.setResultsPerPage(query.getLimit());
    pagination.setOrderBy("date");
    pagination.setSortOrder(SortOrder.DESC);

    AuditTarget selected = auditTarget.orElseThrow();
    ISearchResults<AuditTrailSearchResult> results =
        auditTrail.searchAuditTrail(
            new AuditSearchConfig(
                query.getDateFrom(),
                query.getDateTo(),
                selected.domain(),
                query.getActions(),
                selected.identifier()),
            pagination,
            actor);
    List<ApiV2AuditEvent> events =
        results.getResults().stream()
            .map(result -> event(result, selected.readableFields(), resource.description()))
            .toList();
    return ApiV2ListResult.of(events, results.getTotalHits(), query.getLimit(), query.getPage());
  }

  /**
   * Refuses a window the server cannot honour instead of quietly narrowing it.
   *
   * <p>The previous behaviour answered 200 for a six-year request and returned 183 days, with
   * nothing in the response saying so, so a client paginating to exhaustion believed it had the
   * whole period. Silently returning a different answer to the question asked is worse than
   * refusing: an explicit 400 naming the limit lets the client page the range itself.
   */
  private static void requireSearchableRange(ApiV2AuditQuery query) {
    Date from = query.getDateFrom();
    Date to = query.getDateTo();
    if (from == null || to == null) {
      return;
    }
    if (from.after(to)) {
      throw new ApiV2BadRequestException("errors.api.v2.audit.range.inverted");
    }
    if (Duration.between(from.toInstant(), to.toInstant()).compareTo(MAX_SEARCH_RANGE) > 0) {
      throw new ApiV2BadRequestException(
          "errors.api.v2.audit.range.tooWide", MAX_SEARCH_RANGE.toDays());
    }
  }

  private Optional<AuditTarget> auditTarget(
      ApiV2ResourceRegistration<?, ?> resource, ResolvedTarget target) {
    Class<?> entityType = resource.description().entityType();
    return metadata
        .computeIfAbsent(entityType, ApiV2AuditLog::inspect)
        .flatMap(value -> value.identifier(target.entity()))
        .map(identifier -> new AuditTarget(valueDomain(entityType), identifier, target.fields()));
  }

  private static AuditDomain valueDomain(Class<?> entityType) {
    return entityType.getAnnotation(AuditTrailData.class).auditDomain();
  }

  private static Optional<AuditMetadata> inspect(Class<?> entityType) {
    if (entityType.getAnnotation(AuditTrailData.class) == null) {
      return Optional.empty();
    }
    List<Method> identifiers =
        Arrays.stream(entityType.getMethods())
            .filter(method -> method.getAnnotation(AuditTrailIdentifier.class) != null)
            .toList();
    if (identifiers.isEmpty()) {
      return Optional.empty();
    }
    if (identifiers.size() != 1) {
      throw new IllegalStateException(
          "An audited REST API v2 entity must have one audit identifier: " + entityType.getName());
    }
    return Optional.of(new AuditMetadata(identifiers.get(0)));
  }

  private static ApiV2AuditEvent event(
      AuditTrailSearchResult result,
      FieldSelection readableFields,
      CollectionDescription<?> description) {
    HistoricData event = result.getEvent();
    Map<String, Object> payload = new LinkedHashMap<>();
    Set<String> publicNames = new LinkedHashSet<>();
    description.fields().forEach(field -> publicNames.add(field.name()));
    description.relationships().forEach(relationship -> publicNames.add(relationship.name()));
    event
        .getData()
        .getData()
        .forEach(
            (name, value) -> {
              if (!description.idField().equals(name)
                  && publicNames.contains(name)
                  && readableFields.includes(name, description.idField())) {
                payload.put(name, value);
              }
            });
    return new ApiV2AuditEvent(
        Instant.ofEpochMilli(result.getTimestamp()).toString(),
        event.getSubject(),
        event.getFullName(),
        event.getDomain(),
        event.getAction(),
        event.getDescription(),
        Collections.unmodifiableMap(payload));
  }

  private record AuditMetadata(Method identifierMethod) {

    private Optional<String> identifier(Object entity) {
      try {
        Object value = identifierMethod.invoke(entity);
        return value == null || value.toString().isBlank()
            ? Optional.empty()
            : Optional.of(value.toString());
      } catch (IllegalAccessException | InvocationTargetException ex) {
        LOG.error(
            "Cannot read REST API v2 audit identifier using method [{}]", identifierMethod, ex);
        throw new IllegalStateException("Cannot read the audit identifier", ex);
      }
    }
  }

  private record AuditTarget(
      AuditDomain domain, String identifier, FieldSelection readableFields) {}

  private static final class AuditSearchConfig implements IAuditTrailSearchConfig {

    private Date dateFrom;
    private Date dateTo;
    @Getter private final Set<AuditDomain> domains;
    @Getter private final Set<AuditAction> actions;
    @Getter private final String oid;

    private AuditSearchConfig(
        Date dateFrom, Date dateTo, AuditDomain domain, Set<AuditAction> actions, String oid) {
      this.dateFrom = copy(dateFrom);
      this.dateTo = copy(dateTo);
      this.domains = Set.of(domain);
      this.actions = actions == null ? Set.of() : Set.copyOf(actions);
      this.oid = Objects.requireNonNull(oid, "Audit identifier");
    }

    @Override
    public Date getDateFrom() {
      return copy(dateFrom);
    }

    @Override
    public void setDateFrom(Date dateFrom) {
      this.dateFrom = copy(dateFrom);
    }

    @Override
    public Date getDateTo() {
      return copy(dateTo);
    }

    @Override
    public void setDateTo(Date dateTo) {
      this.dateTo = copy(dateTo);
    }

    @Override
    public Set<String> getUsernames() {
      return Set.of();
    }

    private static Date copy(Date value) {
      return value == null ? null : new Date(value.getTime());
    }
  }
}
