package com.researchspace.webapp.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2PreHandlerProblemResolver;
import com.researchspace.service.MessageSourceUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

/**
 * The v2 problem resolver only helps if it runs before {@link DefaultHandlerExceptionResolver},
 * which is what calls {@code sendError} and produces the container's HTML error page.
 */
class WebConfigExceptionResolverTest {

  @Test
  void registersTheV2ProblemResolverAheadOfSpringsOwnResolvers() {
    WebConfig config = new WebConfig();
    ReflectionTestUtils.setField(
        config,
        "apiV2ControllerAdvice",
        new ApiV2ControllerAdvice(new MessageSourceUtils(new StaticMessageSource())));
    HandlerExceptionResolver springDefault = new DefaultHandlerExceptionResolver();
    List<HandlerExceptionResolver> resolvers = new ArrayList<>(List.of(springDefault));

    config.extendHandlerExceptionResolvers(resolvers);

    assertInstanceOf(ApiV2PreHandlerProblemResolver.class, resolvers.get(0));
    assertSame(springDefault, resolvers.get(1));
  }

  @Test
  void anEmptyModelAndViewCarriesNoViewToRender() {
    assertSame(null, new ModelAndView().getViewName());
  }
}
