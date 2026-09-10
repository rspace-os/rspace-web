package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.field.LocalizedIllegalStateException;
import com.researchspace.model.field.LocalizedUnsupportedOperationException;
import com.researchspace.service.chemistry.ChemistryClientException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageSourceUtilsTest {

  private MessageSourceUtils messages;
  private final Locale enUS = Locale.forLanguageTag("en-US");

  @BeforeEach
  void setUp() {
    messages = new MessageSourceUtils(new JsonMessageSource());
  }

  @Test
  void appliesArgsAgainstExplicitLocale() {
    assertEquals(
        "Name is a required field.",
        messages.getMessage("errors.required", new Object[] {"Name"}, enUS));
  }

  @Test
  void resolvesEveryCodedExceptionThroughTheSameContract() {
    for (Throwable exception :
        List.of(
            new ApiRuntimeException("errors.required", "Name"),
            new ApiAuthenticationException("errors.required", "Name"),
            new MediaContentMismatchException("errors.required", "Name"),
            new ChemistryClientException("errors.required", new Object[] {"Name"}),
            new LocalizedIllegalArgumentException("errors.required", "Name"),
            new LocalizedIllegalStateException("errors.required", "Name"),
            new LocalizedUnsupportedOperationException("errors.required", "Name"))) {
      assertEquals("Name is a required field.", messages.getExceptionMessage(exception));
    }
  }

  @Test
  void formatAppliesListArgsAgainstExplicitLocale() {
    assertEquals(
        "Name is a required field.", messages.format("errors.required", List.of("Name"), enUS));
  }

  @Test
  void formatsCompleteAuthorizationFailure() {
    assertEquals(
        "Unauthorized attempt by alice to change bob's edit-all-work permission in group Lab",
        messages.getMessage(
            "errors.authorization.failure.changeGroupPiEditPermission",
            new Object[] {"alice", "bob", "Lab"},
            enUS));
  }
}
