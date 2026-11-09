package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.booking.api.v2.BookingSettingsController;
import com.researchspace.booking.api.v2.BookingSettingsController.SettingsPatch;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class ApiV2SettingsPatchOpenApiTest {
  @Test
  @SuppressWarnings("unchecked")
  void everyAcceptedNullPatchFieldIsOptionalAndNullable() throws Exception {
    var mapper = new MappingJackson2HttpMessageConverter().getObjectMapper();
    var nulls = mapper.createObjectNode();
    for (var field : SettingsPatch.class.getRecordComponents()) nulls.putNull(field.getName());
    var patch = mapper.treeToValue(nulls, SettingsPatch.class);
    try (var validation = Validation.buildDefaultValidatorFactory()) {
      assertTrue(validation.getValidator().validate(patch).isEmpty());
    }
    var mapping = mock(RequestMappingHandlerMapping.class);
    var method =
        BookingSettingsController.class.getMethod(
            "patchAdmin", SettingsPatch.class, String.class, ApiV2Caller.class);
    when(mapping.getHandlerMethods())
        .thenReturn(
            Map.of(
                RequestMappingInfo.paths("/api/v2/booking-settings/admin")
                    .methods(RequestMethod.PATCH)
                    .build(),
                new HandlerMethod(mock(BookingSettingsController.class), method)));
    var document =
        new ApiV2OpenApiDocumentService(
                new ApiV2OpenApiGenerator(
                    new ApiV2ResourceCatalog(List.of()),
                    com.researchspace.api.v2.openapi.ApiV2OpenApiTestText.english(),
                    "API",
                    "2.0.0"),
                new ApiV2EndpointCatalog(List.of()),
                () -> Stream.of(mapping))
            .generate();
    var components = (Map<String, Object>) document.get("components");
    var schemas = (Map<String, Object>) components.get("schemas");
    var schema = (Map<String, Object>) schemas.get("BookingSettingsControllerSettingsPatch");
    var properties = (Map<String, Object>) schema.get("properties");
    for (var field : SettingsPatch.class.getRecordComponents()) {
      var value = (Map<String, Object>) properties.get(field.getName());
      assertTrue(
          value.get("type") instanceof List<?> types && types.contains("null"), field.getName());
      assertFalse(
          ((List<?>) schema.getOrDefault("required", List.of())).contains(field.getName()),
          field.getName());
      if (value.get("enum") instanceof List<?> choices)
        assertTrue(choices.contains(null), field.getName());
    }
    var hours = (Map<String, Object>) properties.get("openingHours");
    assertEquals(1, hours.get("minItems"));
    assertEquals(7, hours.get("maxItems"));
  }
}
