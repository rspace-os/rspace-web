package com.researchspace.api.v2.resource;

import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.config.ApiV2AuditConfig;
import com.researchspace.api.v2.controller.ApiV2AuditSnapshotConflictException;
import com.researchspace.api.v2.controller.ApiV2AuditUnavailableException;
import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.model.ApiV2AuditPage;
import com.researchspace.api.v2.model.ApiV2AuditQuery;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Searches the existing audit trail for one readable REST API v2 resource. */
@Component
public final class ApiV2AuditLog {

  private static final Logger LOG = LoggerFactory.getLogger(ApiV2AuditLog.class);
  private static final DateTimeFormatter SNAPSHOT_DATE_FORMAT =
      DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);
  private static final Pattern FINGERPRINT = Pattern.compile("^[0-9a-f]{64}$");
  static final Duration MAX_SEARCH_RANGE = Duration.ofDays(183);

  private final ApiV2AuditStrictSearch strictSearch;
  private final Clock clock;
  private final int resultCeiling;
  private final ResourceAccessManager resourceAccessManager;
  private final Map<Class<?>, Optional<AuditMetadata>> metadata = new ConcurrentHashMap<>();

  public ApiV2AuditLog(
      ApiV2AuditStrictSearch strictSearch,
      @Qualifier(ApiV2AuditConfig.AUDIT_CLOCK) Clock clock,
      @Value("${api.v2.audit.resultCeiling:1000}") int resultCeiling) {
    this(strictSearch, clock, resultCeiling, null);
  }

  @Autowired
  public ApiV2AuditLog(
      ApiV2AuditStrictSearch strictSearch,
      @Qualifier(ApiV2AuditConfig.AUDIT_CLOCK) Clock clock,
      @Value("${api.v2.audit.resultCeiling:1000}") int resultCeiling,
      ResourceAccessManager resourceAccessManager) {
    if (resultCeiling < 1) {
      throw new IllegalArgumentException("REST API v2 audit result ceiling must be positive");
    }
    this.strictSearch = strictSearch;
    this.clock = clock;
    this.resultCeiling = resultCeiling;
    this.resourceAccessManager = resourceAccessManager;
  }

  /** Returns an audit page after resource and audit-actor access checks. */
  public ApiV2AuditPage<ApiV2AuditEvent> search(
      ApiV2ResourceRegistration<?, ?> resource, String rawId, ApiV2AuditQuery query, User actor) {
    if (actor == null) {
      throw new ApiV2AuthenticationException();
    }
    ResolvedTarget target =
        resource.requireReadableForAudit(rawId, actor, requireAccessManager(resource));
    SearchWindow window = searchWindow(query);
    Optional<AuditTarget> auditTarget = auditTarget(resource, target);
    List<ApiV2AuditEvent> events =
        auditTarget.map(value -> events(resource, value, query, actor, window)).orElseGet(List::of);
    if (events.size() > resultCeiling) {
      throw new ApiV2BadRequestException("errors.api.v2.audit.results.tooMany");
    }

    List<ApiV2AuditEvent> ordered =
        events.stream()
            .sorted(
                Comparator.comparing(ApiV2AuditEvent::timestamp)
                    .reversed()
                    .thenComparing(ApiV2AuditEvent::eventId, Comparator.reverseOrder()))
            .toList();
    String fingerprint = snapshotFingerprint(ordered);
    if (query.getSnapshotFingerprint() != null
        && !query.getSnapshotFingerprint().equals(fingerprint)) {
      throw new ApiV2AuditSnapshotConflictException();
    }

    int first = Math.min(ordered.size(), Math.max(0, (query.getPage() - 1) * query.getLimit()));
    int last = Math.min(ordered.size(), first + query.getLimit());
    return ApiV2AuditPage.of(
        ordered.subList(first, last),
        ordered.size(),
        query.getLimit(),
        query.getPage(),
        window.snapshotDate().toString(),
        fingerprint);
  }

  private ResourceAccessManager requireAccessManager(ApiV2ResourceRegistration<?, ?> resource) {
    if (resource.resourceAccess().isPresent() && resourceAccessManager == null) {
      throw new IllegalStateException("Resource access manager is required for protected audit");
    }
    return resourceAccessManager;
  }

  private List<ApiV2AuditEvent> events(
      ApiV2ResourceRegistration<?, ?> resource,
      AuditTarget target,
      ApiV2AuditQuery query,
      User actor,
      SearchWindow window) {
    if (!window.fromInclusive().isBefore(window.toExclusive())) {
      return List.of();
    }
    try {
      return strictSearch
          .search(
              new ApiV2AuditStrictSearch.Request(
                  window.fromInclusive(),
                  window.toExclusive(),
                  Set.of(target.domain()),
                  query.getActions() == null ? Set.of() : query.getActions(),
                  target.identifier(),
                  Set.of(),
                  actor,
                  resultCeiling,
                  resource.auditBypassesActorDirectory()))
          .stream()
          .map(
              result ->
                  event(
                      result,
                      target.readableFields(),
                      resource.description(),
                      resource.relatedAuditFields()))
          .toList();
    } catch (ApiV2AuditStrictSearch.StrictReadException ex) {
      throw new ApiV2AuditUnavailableException(ex);
    }
  }

  private SearchWindow searchWindow(ApiV2AuditQuery query) {
    validateSnapshotPair(query);
    requireSearchableRange(query);
    Instant now = clock.instant();
    Instant suppliedFrom = instant(query.getDateFrom());
    Instant suppliedTo = instant(query.getDateTo());
    Instant effectiveTo = suppliedTo == null ? now : suppliedTo;
    Instant effectiveFrom;
    if (suppliedFrom == null) {
      effectiveFrom = effectiveTo.minus(MAX_SEARCH_RANGE);
    } else if (suppliedTo == null && suppliedFrom.isBefore(now.minus(MAX_SEARCH_RANGE))) {
      effectiveFrom = now.minus(MAX_SEARCH_RANGE);
    } else {
      effectiveFrom = suppliedFrom;
    }

    LocalDate latestCompleted = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(1);
    LocalDate latestWithinRequest = latestCompletedDay(effectiveTo);
    LocalDate serverSnapshot =
        latestWithinRequest.isBefore(latestCompleted) ? latestWithinRequest : latestCompleted;
    LocalDate snapshotDate =
        query.getSnapshotDate() == null
            ? serverSnapshot
            : parseSnapshotDate(query.getSnapshotDate());
    if (snapshotDate.isAfter(serverSnapshot)) {
      throw new ApiV2BadRequestException("errors.api.v2.audit.snapshot.invalid");
    }
    Instant toExclusive = snapshotDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return new SearchWindow(effectiveFrom, toExclusive, snapshotDate);
  }

  private static void validateSnapshotPair(ApiV2AuditQuery query) {
    boolean hasDate = query.getSnapshotDate() != null && !query.getSnapshotDate().isBlank();
    boolean hasFingerprint =
        query.getSnapshotFingerprint() != null && !query.getSnapshotFingerprint().isBlank();
    if (hasDate != hasFingerprint
        || (hasFingerprint && !FINGERPRINT.matcher(query.getSnapshotFingerprint()).matches())) {
      throw new ApiV2BadRequestException("errors.api.v2.audit.snapshot.invalid");
    }
    if (!hasDate && (query.getSnapshotDate() != null || query.getSnapshotFingerprint() != null)) {
      throw new ApiV2BadRequestException("errors.api.v2.audit.snapshot.invalid");
    }
  }

  private static LocalDate parseSnapshotDate(String value) {
    try {
      return LocalDate.parse(value, SNAPSHOT_DATE_FORMAT);
    } catch (DateTimeParseException ex) {
      throw new ApiV2BadRequestException("errors.api.v2.audit.snapshot.invalid");
    }
  }

  private static LocalDate latestCompletedDay(Instant inclusiveTo) {
    LocalDate containingDay = LocalDate.ofInstant(inclusiveTo, ZoneOffset.UTC);
    Instant finalMillisecond =
        containingDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
    return finalMillisecond.isAfter(inclusiveTo) ? containingDay.minusDays(1) : containingDay;
  }

  private static Instant instant(java.util.Date date) {
    return date == null ? null : date.toInstant();
  }

  private static void requireSearchableRange(ApiV2AuditQuery query) {
    java.util.Date from = query.getDateFrom();
    java.util.Date to = query.getDateTo();
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
      CollectionDescription<?> description,
      Set<String> relatedAuditFields) {
    HistoricData source = result.getEvent();
    Map<String, Object> payload = new LinkedHashMap<>();
    Set<String> publicNames = new LinkedHashSet<>();
    description.fields().forEach(field -> publicNames.add(field.name()));
    description.relationships().forEach(relationship -> publicNames.add(relationship.name()));
    source
        .getData()
        .getData()
        .forEach(
            (name, value) -> {
              if (!description.idField().equals(name)
                  && ((publicNames.contains(name)
                          && readableFields.includes(name, description.idField()))
                      || relatedAuditFields.contains(name))) {
                payload.put(name, value);
              }
            });
    Object targetValue = source.getData().getData().get(description.idField());
    ApiV2AuditEvent withoutId =
        new ApiV2AuditEvent(
            null,
            Instant.ofEpochMilli(result.getTimestamp()).toString(),
            source.getSubject(),
            source.getFullName(),
            source.getDomain(),
            source.getAction(),
            source.getDescription(),
            Collections.unmodifiableMap(payload),
            targetValue == null ? null : targetValue.toString());
    return new ApiV2AuditEvent(
        eventId(withoutId),
        withoutId.timestamp(),
        withoutId.username(),
        withoutId.fullName(),
        withoutId.domain(),
        withoutId.action(),
        withoutId.description(),
        withoutId.payload(),
        withoutId.target());
  }

  static String eventId(ApiV2AuditEvent event) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, event.timestamp());
        writeString(output, event.username());
        writeString(output, event.fullName());
        writeString(output, event.domain() == null ? null : event.domain().name());
        writeString(output, event.action() == null ? null : event.action().name());
        writeString(output, event.description());
        writeValue(output, event.payload());
      }
      return hex(digest(bytes.toByteArray()));
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot canonicalize an audit event", ex);
    }
  }

  static String snapshotFingerprint(List<ApiV2AuditEvent> events) {
    MessageDigest digest = sha256();
    digest.update(ByteBuffer.allocate(Long.BYTES).putLong(events.size()).array());
    for (ApiV2AuditEvent event : events) {
      byte[] id = event.eventId().getBytes(StandardCharsets.UTF_8);
      digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(id.length).array());
      digest.update(id);
    }
    return hex(digest.digest());
  }

  private static void writeValue(DataOutputStream output, Object value) throws IOException {
    if (value == null) {
      output.writeByte('N');
    } else if (value instanceof Map<?, ?> map) {
      output.writeByte('M');
      TreeMap<String, Object> ordered = new TreeMap<>();
      map.forEach((key, item) -> ordered.put(String.valueOf(key), item));
      output.writeInt(ordered.size());
      for (Map.Entry<String, Object> entry : ordered.entrySet()) {
        writeString(output, entry.getKey());
        writeValue(output, entry.getValue());
      }
    } else if (value instanceof Iterable<?> iterable) {
      output.writeByte('L');
      List<?> values =
          iterable instanceof List<?> list
              ? list
              : java.util.stream.StreamSupport.stream(iterable.spliterator(), false).toList();
      output.writeInt(values.size());
      for (Object item : values) {
        writeValue(output, item);
      }
    } else if (value instanceof Number number) {
      output.writeByte('D');
      writeString(output, new BigDecimal(number.toString()).stripTrailingZeros().toPlainString());
    } else if (value instanceof Boolean bool) {
      output.writeByte('B');
      output.writeBoolean(bool);
    } else {
      output.writeByte('S');
      writeString(output, value.toString());
    }
  }

  private static void writeString(DataOutputStream output, String value) throws IOException {
    if (value == null) {
      output.writeInt(-1);
      return;
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    output.writeInt(bytes.length);
    output.write(bytes);
  }

  private static byte[] digest(byte[] value) {
    return sha256().digest(value);
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
      result.append(Character.forDigit(value & 0x0f, 16));
    }
    return result.toString();
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

  private record SearchWindow(Instant fromInclusive, Instant toExclusive, LocalDate snapshotDate) {}
}
