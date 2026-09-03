package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.JsonMessageSource;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every message code the external metadata update asks for must resolve to real text.
 *
 * <p>Worth pinning because a missing code does not fail loudly: {@code
 * applicationContext-resources.xml} sets {@code useCodeAsDefaultMessage=true}, so a typo or a
 * deleted catalogue entry reaches the user, and the audit trail, as the bare code
 * "errors.inventory.identifier.externalUpdateFailed". The service's own tests mock the message
 * source and assert the code that was requested, which pins the call site but cannot see whether
 * the catalogue answers it.
 *
 * <p>Resolved through {@link JsonMessageSource}, the class production uses, and not by reading the
 * catalogue file. How a code maps onto the JSON - dot flattening, the empty prefix for {@code
 * server.*} - is the message source's business, so a test that walked the file itself would be
 * restating those rules rather than exercising them. The catalogue is on the test classpath (see
 * the {@code i18n/locales} resource in {@code pom.xml}), which is what makes this a fast unit test.
 */
class ExternalUpdateMessageKeysTest {

  private static final JsonMessageSource MESSAGES = new JsonMessageSource();
  private static final Locale EN_US = Locale.forLanguageTag("en-US");

  /** A name no catalogue entry contains, so finding it proves it was interpolated. */
  private static final String PROVIDER = "Zz-Provider";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "inventory.identifier.externalUpdated",
        "errors.inventory.identifier.externalUpdateFailed",
        "errors.inventory.identifier.externalUpdateNotPossibleB2inst",
        "errors.inventory.identifier.externalUpdateNotPossibleDataCite",
        // asked for by B2instConnectorImpl, but they reach a user only by being interpolated into
        // externalUpdateFailed above, so a missing one shows up in the same place
        "errors.inventory.identifier.b2instHttpStatus",
        "errors.inventory.identifier.b2instUnreachable",
        "errors.inventory.identifier.b2instNoCommunity",
        "errors.inventory.identifier.b2instNoSubmitAction"
      })
  void everyCodeResolvesToRealText(String code) {
    // an unknown code throws NoSuchMessageException here, which is the failure this exists to catch
    String message = MESSAGES.getMessage(code, new Object[] {PROVIDER, "a provider detail"}, EN_US);

    assertNotEquals(code, message, "resolved to its own code, so the catalogue has no entry");
    assertTrue(message.length() > code.length() / 2, "suspiciously short message: " + message);
  }

  /** The provider name is passed as {0}, so each of these must actually place it. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "inventory.identifier.externalUpdated",
        "errors.inventory.identifier.externalUpdateFailed",
        "errors.inventory.identifier.externalUpdateNotPossibleB2inst",
        "errors.inventory.identifier.externalUpdateNotPossibleDataCite"
      })
  void everyProviderMessageNamesTheProvider(String code) {
    String message = MESSAGES.getMessage(code, new Object[] {PROVIDER, ""}, EN_US);

    assertTrue(message.contains(PROVIDER), code + " must name the provider, but said: " + message);
  }
}
