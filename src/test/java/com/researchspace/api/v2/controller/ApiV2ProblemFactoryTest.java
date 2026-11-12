package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;

class ApiV2ProblemFactoryTest {
  @Test
  void separatesStableTitlesFromOccurrenceDetailsAndFallsBackForMissingTitles() {
    StaticMessageSource source = new StaticMessageSource();
    source.setUseCodeAsDefaultMessage(true);
    source.addMessage("errors.api.v2.invalidRequest", Locale.getDefault(), "Invalid request");
    source.addMessage("errors.api.v2.limit", Locale.getDefault(), "Limit: {0}");
    source.addMessage("errors.api.v2.limitTitle", Locale.getDefault(), "Limit exceeded");
    source.addMessage("errors.api.v2.dynamic", Locale.getDefault(), "Dynamic: {0}");
    var factory = new ApiV2ProblemFactory(new MessageSourceUtils(source));
    var first = factory.problem(HttpStatus.BAD_REQUEST, "errors.api.v2.limit", 1).getBody();
    var second = factory.problem(HttpStatus.BAD_REQUEST, "errors.api.v2.limit", 2).getBody();
    assertEquals("Limit exceeded", first.title());
    assertEquals(first.title(), second.title());
    assertEquals("Limit: 1", first.detail());
    assertEquals("Limit: 2", second.detail());
    assertEquals(
        "Invalid request",
        factory.problem(HttpStatus.BAD_REQUEST, "errors.api.v2.dynamic", 3).getBody().title());
    assertNull(
        factory.problem(HttpStatus.BAD_REQUEST, "errors.api.v2.invalidRequest").getBody().detail());
    var invalid = List.of(new ApiV2Problem.InvalidParam("field", "required"));
    var explicit =
        factory
            .problem(
                HttpStatus.BAD_REQUEST,
                "errors.api.v2.invalidRequest",
                "Field is required",
                invalid,
                Map.of("limit", 5))
            .getBody();
    assertEquals("Invalid request", explicit.title());
    assertEquals("Field is required", explicit.detail());
    assertEquals(invalid, explicit.invalidParams());
    assertEquals(Map.of("limit", 5), explicit.extensions());
  }
}
