package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.controller.ApiControllerAdvice;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.model.Community;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.oauth.OAuthApp;
import com.researchspace.model.record.TestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ImportedDomainMessagesTest {

  private final TestApiControllerAdvice advice = new TestApiControllerAdvice();

  @Test
  void communityAdminRoleMessageIsResolvedInApiResponse() {
    User user = TestFactory.createAnyUser("ordinaryUser");
    user.addRole(Role.USER_ROLE);

    LocalizedIllegalArgumentException exception =
        assertThrows(LocalizedIllegalArgumentException.class, () -> new Community().addAdmin(user));

    assertEquals("A community admin must have  an admin role", apiErrorFor(exception).getMessage());
  }

  @Test
  void oauthAppNameMessageIsResolvedInApiResponse() {
    User user = TestFactory.createAnyUser("appOwner");

    LocalizedIllegalArgumentException exception =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> new OAuthApp(user, "  ", "clientId", "secret"));

    assertEquals("name must be non-empty", apiErrorFor(exception).getMessage());
  }

  @Test
  void globalIdentifierRequiredAndFormatMessagesAreResolvedInApiResponses() {
    LocalizedIllegalArgumentException required =
        assertThrows(LocalizedIllegalArgumentException.class, () -> new GlobalIdentifier(""));
    assertEquals("oid cannot be null or empty", apiErrorFor(required).getMessage());

    String oid = "INVALID_ID";
    LocalizedIllegalArgumentException invalidFormat =
        assertThrows(LocalizedIllegalArgumentException.class, () -> new GlobalIdentifier(oid));
    assertEquals(
        "OID " + oid + " did not match pattern " + GlobalIdentifier.OID_PATTERN_STRING,
        apiErrorFor(invalidFormat).getMessage());
  }

  @Test
  void invalidNfsLinkTypeUsesItsValueAndNullStillRaisesIllegalArgumentException() {
    NfsElement element = new NfsElement(62L, "/readme.txt");
    String invalidLinkType = "symlink";

    LocalizedIllegalArgumentException invalidType =
        assertThrows(
            LocalizedIllegalArgumentException.class, () -> element.setLinkType(invalidLinkType));
    assertEquals("unexpected link type: " + invalidLinkType, apiErrorFor(invalidType).getMessage());

    LocalizedIllegalArgumentException nullType =
        assertThrows(LocalizedIllegalArgumentException.class, () -> element.setLinkType(null));
    assertEquals("unexpected link type: null", apiErrorFor(nullType).getMessage());
  }

  private ApiError apiErrorFor(LocalizedIllegalArgumentException exception) {
    ResponseEntity<Object> response =
        advice.handleLocalizedIllegalArgumentException(exception, null);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
    ApiError apiError = assertInstanceOf(ApiError.class, response.getBody());
    assertEquals(ApiErrorCodes.ILLEGAL_ARGUMENT.getCode(), apiError.getInternalCode());
    return apiError;
  }

  private static class TestApiControllerAdvice extends ApiControllerAdvice {
    TestApiControllerAdvice() {
      messages = new MessageSourceUtils(new JsonMessageSource());
    }
  }
}
