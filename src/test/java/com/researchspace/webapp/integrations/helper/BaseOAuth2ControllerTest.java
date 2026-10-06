package com.researchspace.webapp.integrations.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import com.researchspace.session.SessionAttributeUtils;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseOAuth2ControllerTest {
  @Test
  void stateIsRequiredMatchesAndIsConsumedEvenOnFailure() {
    BaseOAuth2Controller controller =
        new BaseOAuth2Controller() {
          @Override
          public String getText(String key) {
            return key;
          }
        };
    Map<String, Object> session = new HashMap<>();
    try (var attributes = mockStatic(SessionAttributeUtils.class)) {
      attributes
          .when(() -> SessionAttributeUtils.setSessionAttribute(anyString(), any()))
          .thenAnswer(call -> session.put(call.getArgument(0), call.getArgument(1)));
      attributes
          .when(() -> SessionAttributeUtils.removeSessionAttribute(anyString()))
          .thenAnswer(call -> session.remove(call.getArgument(0)));
      assertThrows(IllegalStateException.class, () -> controller.verifyStateParameter("forged"));
      String state = controller.generateState();
      assertEquals(32, state.length());
      controller.verifyStateParameter(state);
      assertNull(session.get(SessionAttributeUtils.RS_OAUTH_STATE));
      assertThrows(IllegalStateException.class, () -> controller.verifyStateParameter(state));
      controller.generateState();
      assertThrows(
          IllegalStateException.class, () -> controller.verifyStateParameter((String) null));
      assertNull(session.get(SessionAttributeUtils.RS_OAUTH_STATE));
      controller.generateState();
      assertThrows(IllegalStateException.class, () -> controller.verifyStateParameter("wrong"));
      assertNull(session.get(SessionAttributeUtils.RS_OAUTH_STATE));
    }
  }
}
