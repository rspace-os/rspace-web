package com.researchspace.api.v2.openapi;

import java.util.Locale;
import java.util.Objects;
import org.springframework.context.MessageSource;

/**
 * Resolves the human-readable text that the shared OpenAPI builders emit from the server message
 * catalog. Identifiers such as operation IDs, component names, paths and media types are contract
 * and stay in code; text documented by a resource or endpoint is used as that feature supplies it.
 */
public final class ApiV2OpenApiText {

  /**
   * The generated document is served and committed in English. To localize it, resolve with the
   * request locale instead and cache one document per locale.
   */
  private static final Locale LOCALE = Locale.ENGLISH;

  private final MessageSource messages;

  public ApiV2OpenApiText(MessageSource messages) {
    this.messages = Objects.requireNonNull(messages, "Message source");
  }

  /** Returns the catalog text for the key, formatting any arguments into its placeholders. */
  public String get(Key key, Object... arguments) {
    String code = key.code();
    String text = messages.getMessage(code, arguments, null, LOCALE);
    if (text == null || text.equals(code)) {
      throw new IllegalStateException("Missing OpenAPI text " + code);
    }
    return text;
  }

  /** Catalog keys under {@code api.v2.openapi}. */
  public enum Key {
    INFO_TITLE("info.title"),
    INFO_DESCRIPTION("info.description"),
    SERVER_DESCRIPTION("server.description"),
    SECURITY_BROWSER_SESSION("security.browserSession"),

    HEADER_ETAG("headers.etag"),
    HEADER_RATE_LIMIT_LIMIT("headers.rateLimitLimit"),
    HEADER_RATE_LIMIT_REMAINING("headers.rateLimitRemaining"),
    HEADER_RATE_LIMIT_WAIT("headers.rateLimitWait"),
    HEADER_RATE_LIMIT_MIN_WAIT("headers.rateLimitMinWait"),
    HEADER_RATE_LIMIT_INTERVALS("headers.rateLimitIntervals"),
    HEADER_PRIVATE_NO_STORE("headers.privateNoStore"),

    RESPONSE_BAD_REQUEST("responses.badRequest"),
    RESPONSE_UNAUTHENTICATED("responses.unauthenticated"),
    RESPONSE_FORBIDDEN("responses.forbidden"),
    RESPONSE_NOT_FOUND("responses.notFound"),
    RESPONSE_NOT_ACCEPTABLE("responses.notAcceptable"),
    RESPONSE_UNSUPPORTED_MEDIA_TYPE("responses.unsupportedMediaType"),
    RESPONSE_BULK_LIMIT("responses.bulkLimit"),
    RESPONSE_TOO_MANY_REQUESTS("responses.tooManyRequests"),
    RESPONSE_UNEXPECTED_ERROR("responses.unexpectedError"),
    RESPONSE_IF_MATCH_REQUIRED("responses.ifMatchRequired"),
    RESPONSE_PERMANENTLY_REMOVED("responses.permanentlyRemoved"),
    RESPONSE_SUCCESS("responses.success"),
    RESPONSE_FAILURE("responses.failure"),

    LIST_SUMMARY("operations.list.summary"),
    LIST_DESCRIPTION("operations.list.description"),
    LIST_SUCCESS("operations.list.success"),
    COUNT_SUMMARY("operations.count.summary"),
    COUNT_DESCRIPTION("operations.count.description"),
    COUNT_SUCCESS("operations.count.success"),
    READ_SUMMARY("operations.read.summary"),
    READ_DESCRIPTION("operations.read.description"),
    READ_SUCCESS("operations.read.success"),
    CREATE_SUMMARY("operations.create.summary"),
    CREATE_DESCRIPTION("operations.create.description"),
    CREATE_SUCCESS("operations.create.success"),
    BULK_CREATE_SUMMARY("operations.bulkCreate.summary"),
    BULK_CREATE_DESCRIPTION("operations.bulkCreate.description"),
    BULK_CREATE_SUCCESS("operations.bulkCreate.success"),
    UPDATE_SUMMARY("operations.update.summary"),
    UPDATE_DESCRIPTION("operations.update.description"),
    UPDATE_SUCCESS("operations.update.success"),
    BULK_UPDATE_SUMMARY("operations.bulkUpdate.summary"),
    BULK_UPDATE_DESCRIPTION("operations.bulkUpdate.description"),
    BULK_UPDATE_SUCCESS("operations.bulkUpdate.success"),
    DELETE_SUMMARY("operations.delete.summary"),
    DELETE_DESCRIPTION("operations.delete.description"),
    DELETE_SUCCESS("operations.delete.success"),
    ARCHIVE_SUMMARY("operations.archive.summary"),
    ARCHIVE_DESCRIPTION("operations.archive.description"),
    ARCHIVE_PERMANENT_DESCRIPTION("operations.archive.permanentDescription"),
    BULK_DELETE_SUMMARY("operations.bulkDelete.summary"),
    BULK_DELETE_DESCRIPTION("operations.bulkDelete.description"),
    BULK_DELETE_SUCCESS("operations.bulkDelete.success"),
    BULK_ARCHIVE_SUMMARY("operations.bulkArchive.summary"),
    BULK_ARCHIVE_DESCRIPTION("operations.bulkArchive.description"),

    PARAMETER_ID("parameters.id"),
    PARAMETER_IF_MATCH("parameters.ifMatch"),
    PARAMETER_PERMANENT("parameters.permanent"),
    PARAMETER_WHERE("parameters.where"),
    PARAMETER_WHERE_WITH_RUNTIME_FIELDS("parameters.whereWithRuntimeFields"),
    PARAMETER_SORT("parameters.sort"),
    PARAMETER_PAGE("parameters.page"),
    PARAMETER_LIMIT("parameters.limit"),
    PARAMETER_TOTALS("parameters.totals"),
    PARAMETER_MAX("parameters.max"),
    PARAMETER_DEPTH("parameters.depth"),
    PARAMETER_FIELDS("parameters.fields"),
    PARAMETER_EXCLUDE("parameters.exclude"),

    LIST_TOTAL_DOCS("schemas.totalDocs"),
    LIST_TOTAL_IS_LOWER_BOUND("schemas.totalIsLowerBound"),
    GLOBAL_ID("schemas.globalId"),
    GLOBAL_ID_PREFIXES("schemas.globalIdPrefixes"),

    RUNTIME_FIELDS_SUCCESS("runtimeFields.success"),
    RUNTIME_FIELDS_NAMESPACE("runtimeFields.namespace"),
    RUNTIME_FIELDS_SEARCH("runtimeFields.search"),
    RUNTIME_FIELDS_IDS("runtimeFields.ids"),
    RUNTIME_FIELDS_PAGE("runtimeFields.page"),
    RUNTIME_FIELDS_LIMIT("runtimeFields.limit"),

    ACCESS_READ_SUMMARY("resourceAccess.read.summary"),
    ACCESS_REPLACE_SUMMARY("resourceAccess.replace.summary"),
    ACCESS_LEAVE_SUMMARY("resourceAccess.leave.summary"),
    ACCESS_SEARCH_SUMMARY("resourceAccess.search.summary"),
    ACCESS_MANAGING_CALLERS("resourceAccess.managingCallers"),
    ACCESS_AUTHORIZED_CALLERS("resourceAccess.authorizedCallers"),
    ACCESS_IF_MATCH("resourceAccess.ifMatch"),
    ACCESS_QUERY("resourceAccess.query"),
    ACCESS_LIMIT("resourceAccess.limit"),
    ACCESS_ASSIGNMENT_ABSENT("resourceAccess.assignmentAbsent"),
    ACCESS_GRANTEES("resourceAccess.grantees"),
    ACCESS_DOCUMENT("resourceAccess.document"),
    ACCESS_OWNER_REQUIRED("resourceAccess.ownerRequired"),
    ACCESS_STALE("resourceAccess.stale"),
    RESPONSE_VERSION_CHANGED("responses.versionChanged"),
    AUDIT_DESCRIPTION("audit.description"),
    AUDIT_ACCESS("audit.access"),
    AUDIT_START("audit.start"),
    AUDIT_END("audit.end"),
    AUDIT_SEARCH("audit.search"),
    AUDIT_ACTIONS("audit.actions"),
    AUDIT_SUCCESS("audit.success"),
    AUDIT_INVALID("audit.invalid"),
    AUDIT_UNAVAILABLE("audit.unavailable"),
    AUDIT_SUMMARY("audit.summary"),
    PARAMETER_IDEMPOTENCY_KEY("parameters.idempotencyKey"),
    RESPONSE_CREATE_RETRY("responses.createRetry"),
    PARAMETER_GENERIC_FIELDSET("parameters.genericFieldset");

    static final String PREFIX = "api.v2.openapi.";

    private final String code;

    Key(String key) {
      this.code = PREFIX + key;
    }

    /** The full catalog key. */
    public String code() {
      return code;
    }
  }
}
