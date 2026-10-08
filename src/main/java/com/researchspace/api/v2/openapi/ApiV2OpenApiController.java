package com.researchspace.api.v2.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ApiV2OpenApiController implements ApplicationListener<ContextRefreshedEvent> {

  private final ApiV2OpenApiDocumentService documents;
  private final ObjectMapper objectMapper;
  private final boolean production;
  private final AtomicReference<CachedDocument> document = new AtomicReference<>();

  public ApiV2OpenApiController(
      ApiV2OpenApiDocumentService documents, ObjectMapper objectMapper, Environment environment) {
    this.documents = documents;
    this.objectMapper = objectMapper;
    boolean productionProfile = environment.acceptsProfiles(Profiles.of("prod", "prod-test"));
    boolean developmentProfile = environment.acceptsProfiles(Profiles.of("dev", "run"));
    this.production = productionProfile && !developmentProfile;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    document.set(production ? generate() : null);
  }

  @GetMapping(value = "/api/v2/openapi.json", produces = "application/json")
  @Operation(
      operationId = "getApiV2OpenApiDocument",
      summary = "Get the REST API v2 OpenAPI document",
      description =
          "Returns the generated OpenAPI 3.1 contract. Production responses are privately cached"
              + " for one hour and support conditional requests; development responses use"
              + " no-store.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current OpenAPI document.",
            headers = {
              @Header(
                  name = "Cache-Control",
                  description = "Environment-dependent cache policy.",
                  schema = @Schema(type = "string")),
              @Header(
                  name = "ETag",
                  description = "Production entity tag.",
                  schema = @Schema(type = "string"))
            }),
        @ApiResponse(
            responseCode = "304",
            description = "The production document matches If-None-Match.",
            headers = {
              @Header(name = "Cache-Control", schema = @Schema(type = "string")),
              @Header(name = "ETag", schema = @Schema(type = "string"))
            }),
        @ApiResponse(responseCode = "429", description = "The request was throttled."),
        @ApiResponse(responseCode = "500", description = "Document generation failed.")
      })
  public ResponseEntity<Map<String, Object>> openApi(HttpServletRequest request) {
    CachedDocument current = production ? productionDocument() : generate();
    if (production && etagMatches(request.getHeader(HttpHeaders.IF_NONE_MATCH), current.etag())) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
          .cacheControl(productionCacheControl())
          .eTag(current.etag())
          .build();
    }
    CacheControl cacheControl = production ? productionCacheControl() : CacheControl.noStore();
    ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(cacheControl);
    if (production) {
      response.eTag(current.etag());
    }
    return response.body(current.value());
  }

  private CachedDocument productionDocument() {
    CachedDocument cached = document.get();
    if (cached != null) {
      return cached;
    }
    CachedDocument generated = generate();
    document.compareAndSet(null, generated);
    return document.get();
  }

  private CachedDocument generate() {
    Map<String, Object> value = documents.generate();
    try {
      byte[] json = objectMapper.writeValueAsBytes(value);
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
      return new CachedDocument(value, "\"" + HexFormat.of().formatHex(digest) + "\"");
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Generated OpenAPI document cannot be serialized", ex);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  private static CacheControl productionCacheControl() {
    return CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().mustRevalidate();
  }

  private static boolean etagMatches(String ifNoneMatch, String etag) {
    if (ifNoneMatch == null) {
      return false;
    }
    for (String candidate : ifNoneMatch.split(",")) {
      String normalized = candidate.trim();
      if (normalized.equals("*") || normalized.equals(etag) || normalized.equals("W/" + etag)) {
        return true;
      }
    }
    return false;
  }

  private record CachedDocument(Map<String, Object> value, String etag) {}
}
