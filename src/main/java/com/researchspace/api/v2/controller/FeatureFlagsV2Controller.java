package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.featureflags.FeatureFlagNotFoundException;
import com.researchspace.featureflags.FeatureFlagPermissionException;
import com.researchspace.featureflags.FeatureFlagReadOnlyException;
import com.researchspace.featureflags.FeatureFlagState;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.MessageSourceUtils;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@ApiController
@RequestMapping("/api/v2/feature-flags")
public class FeatureFlagsV2Controller {

  @Autowired private FeatureFlagManager featureFlagManager;
  @Autowired private MessageSourceUtils messages;

  @GetMapping
  public ApiV2FeatureFlags getFeatureFlags(
      @RequestAttribute(name = "user", required = false) User user) {
    return new ApiV2FeatureFlags(featureFlagManager.getFeatureFlagStates(user));
  }

  @PutMapping("/{flagName}/override")
  public ResponseEntity<Void> setOverride(
      @PathVariable String flagName,
      @RequestBody FeatureFlagValueRequest request,
      @RequestAttribute(name = "user", required = false) User user) {
    featureFlagManager.setUserOverride(flagName, requiredValue(request), user);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{flagName}/override")
  public ResponseEntity<Void> clearOverride(
      @PathVariable String flagName, @RequestAttribute(name = "user", required = false) User user) {
    featureFlagManager.clearUserOverride(flagName, user);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{flagName}/baseline")
  public ResponseEntity<Void> setBaseline(
      @PathVariable String flagName,
      @RequestBody FeatureFlagValueRequest request,
      @RequestAttribute(name = "user", required = false) User user) {
    featureFlagManager.setBaseline(flagName, requiredValue(request), user);
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(FeatureFlagNotFoundException.class)
  public ResponseEntity<ApiV2Problem> handleNotFound(FeatureFlagNotFoundException ex) {
    return ApiV2Problem.response(
        HttpStatus.NOT_FOUND,
        messages.getMessage("api.v2.featureFlags.errors.unknown", new Object[] {ex.getFlagName()}));
  }

  @ExceptionHandler(FeatureFlagPermissionException.class)
  public ResponseEntity<ApiV2Problem> handleForbidden(FeatureFlagPermissionException ex) {
    return ApiV2Problem.response(
        HttpStatus.FORBIDDEN, messages.getMessage("api.v2.featureFlags.errors.notPermitted"));
  }

  @ExceptionHandler(FeatureFlagReadOnlyException.class)
  public ResponseEntity<ApiV2Problem> handleReadOnly(FeatureFlagReadOnlyException ex) {
    return ApiV2Problem.response(
        HttpStatus.CONFLICT,
        messages.getMessage(
            "api.v2.featureFlags.errors.readOnly", new Object[] {ex.getFlagName()}));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiV2Problem> handleResponseStatus(ResponseStatusException ex) {
    HttpStatus status = ex.getStatus();
    return ApiV2Problem.response(status, ex.getReason());
  }

  private boolean requiredValue(FeatureFlagValueRequest request) {
    if (request == null || request.value() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, messages.getMessage("api.v2.featureFlags.errors.valueRequired"));
    }
    return request.value();
  }

  public record ApiV2FeatureFlags(Map<String, FeatureFlagState> flags) {}

  public record FeatureFlagValueRequest(@JsonProperty("value") Boolean value) {}
}
