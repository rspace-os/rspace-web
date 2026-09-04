package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.ModelAndView;

class ControllerExceptionHandlerTest {

  @Test
  void resolvesLocalizedModelExceptionForMvcResponse() {
    ControllerExceptionHandler handler = new ControllerExceptionHandler();
    ReflectionTestUtils.setField(
        handler, "messages", new MessageSourceUtils(new JsonMessageSource()));

    ModelAndView result =
        handler.handleExceptions(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            new LocalizedIllegalArgumentException(
                "validation.settings.invalidBoolean",
                new IllegalArgumentException("developer-only cause"),
                "perhaps"));

    assertEquals(ControllerExceptionHandler.NON_AJAX_ERROR_VIEW_NAME, result.getViewName());
    assertEquals(
        "[perhaps] is not a boolean value\n\n",
        result.getModel().get(ControllerExceptionHandler.EXCEPTION_MESSAGE_ATTR_NAME));
  }
}
