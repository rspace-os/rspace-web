package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

public class ErrorListTest {

  private final BiFunction<String, Object[], String> resolver =
      (code, args) -> "resolved " + code + " " + args[0];

  @Test
  public void mergedCodesResolveOnceAfterLegacyMessages() {
    ErrorList errors = ErrorList.of("legacy message");
    ErrorList other = new ErrorList();
    other.addErrorMsgCode("some.code", 7);
    errors.addErrorList(other);

    errors.resolveMessageCodes(resolver);
    errors.resolveMessageCodes(resolver);

    assertEquals(List.of("legacy message", "resolved some.code 7"), errors.getErrorMessages());
  }

  @Test
  public void unresolvedCodesCountAsErrorsAndShowAsKeys() {
    ErrorList errors = new ErrorList();
    errors.addErrorMsgCode("some.code", 7);

    assertTrue(errors.hasErrorMessages());
    assertEquals(List.of("some.code"), errors.getErrorMessages());
  }

  @Test
  public void resolveMessagesAndFormatLeavesListUnchanged() {
    ErrorList errors = new ErrorList();
    errors.addErrorMsgCode("some.code", 7);

    assertEquals("resolved some.code 7", errors.resolveMessagesAndFormat(resolver));
    assertEquals(List.of("some.code"), errors.getErrorMessages());
  }
}
