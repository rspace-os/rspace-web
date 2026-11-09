package com.researchspace.api.v2.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.cfg.MutableCoercionConfig;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.DocumentValidationException.Violation;
import com.researchspace.webapp.config.WebDefaultConfig;
import java.util.List;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/** Object mappers used for API v2 request bodies. */
public final class ApiV2JsonMappers {
  private static final String INVALID_REQUEST = "errors.api.v2.invalidRequest";

  private ApiV2JsonMappers() {}

  /** Registers only API v2 body types, leaving the converter's shared mapper unchanged. */
  public static void register(
      MappingJackson2HttpMessageConverter converter, Class<?>... strictTypes) {
    ObjectMapper base = converter.getObjectMapper();
    register(converter, ApiV2JsonBody.class, limited(base));
    if (strictTypes.length > 0) {
      // Bean Validation owns HTTP records' required fields and nullable patch components.
      ObjectMapper strictBody =
          strict(base)
              .disable(
                  DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                  DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
      for (Class<?> type : strictTypes) {
        register(converter, type, strictBody);
      }
    }
  }

  /** Whether a body type uses the registered strict HTTP binding contract. */
  public static boolean isStrictBody(MappingJackson2HttpMessageConverter converter, Class<?> type) {
    return type != ApiV2JsonBody.class && !converter.getObjectMappersForType(type).isEmpty();
  }

  private static void register(
      MappingJackson2HttpMessageConverter converter, Class<?> type, ObjectMapper mapper) {
    converter.registerObjectMappersForType(
        type,
        mappers -> converter.getSupportedMediaTypes().forEach(media -> mappers.put(media, mapper)));
  }

  /** Returns a copy of the converter mapper with API v2 JSON stream limits. */
  public static ObjectMapper limited(ObjectMapper base) {
    ObjectMapper mapper = base.copy();
    mapper.getFactory().setStreamReadConstraints(WebDefaultConfig.streamReadConstraints());
    return mapper;
  }

  /** Returns a limited copy with strict fixed-shape request binding. */
  public static ObjectMapper strict(ObjectMapper base) {
    ObjectMapper mapper = limited(base);
    mapper.enable(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
        DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
        DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    mapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

    MutableCoercionConfig textual = mapper.coercionConfigFor(LogicalType.Textual);
    textual.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
    textual.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
    textual.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);

    for (LogicalType logicalType :
        List.of(LogicalType.Integer, LogicalType.Float, LogicalType.Boolean)) {
      MutableCoercionConfig coercions = mapper.coercionConfigFor(logicalType);
      coercions.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
      coercions.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
      if (logicalType == LogicalType.Boolean) {
        coercions.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        coercions.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
      }
    }
    return mapper;
  }

  /** Maps a strict Jackson binding failure to the API v2 validation response model. */
  public static DocumentValidationException validationException(
      MismatchedInputException exception) {
    Reason reason = reason(exception);
    String field = path(exception);
    if (exception instanceof UnrecognizedPropertyException unknown) {
      field = appendField(field, unknown.getPropertyName());
    }
    return new DocumentValidationException(INVALID_REQUEST, List.of(new Violation(field, reason)));
  }

  private static Reason reason(MismatchedInputException exception) {
    if (exception instanceof UnrecognizedPropertyException) {
      return Reason.UNKNOWN_FIELD;
    }
    if (exception instanceof InvalidNullException) {
      return Reason.NULL_NOT_ALLOWED;
    }
    // Jackson reports both creator failures as MismatchedInputException without a reason code.
    String message = exception.getOriginalMessage();
    if (message.startsWith("Missing creator property")
        || message.startsWith("Missing required creator property")) {
      return Reason.REQUIRED;
    }
    if (message.startsWith("Null value for creator property")) {
      return Reason.NULL_NOT_ALLOWED;
    }
    return Reason.WRONG_TYPE;
  }

  private static String path(MismatchedInputException exception) {
    StringBuilder path = new StringBuilder();
    for (Reference reference : exception.getPath()) {
      if (reference.getFieldName() != null) {
        appendField(path, reference.getFieldName());
      } else if (reference.getIndex() >= 0) {
        path.append('[').append(reference.getIndex()).append(']');
      }
    }
    return path.toString();
  }

  private static void appendField(StringBuilder path, String field) {
    if (field == null || field.isEmpty()) {
      return;
    }
    if (path.length() > 0) {
      path.append('.');
    }
    path.append(field);
  }

  private static String appendField(String path, String field) {
    if (field == null || field.isEmpty()) {
      return path;
    }
    if (path.equals(field) || path.endsWith("." + field)) {
      return path;
    }
    return path.isEmpty() ? field : path + "." + field;
  }
}
