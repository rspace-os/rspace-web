package com.researchspace.webapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

class WebConfigStorybookRouteTest {

  @Test
  void registersStorybookResourcesAndEntryPointWhenDeploymentPropertyIsEnabled() {
    WebConfig config = configWithStorybookEnabled(true);
    ResourceHandlerRegistry resources =
        new ResourceHandlerRegistry(new StaticApplicationContext(), new MockServletContext());

    config.addResourceHandlers(resources);
    ViewControllerRegistry views = new ViewControllerRegistry(new StaticApplicationContext());
    config.addViewControllers(views);

    assertTrue(resources.hasMappingForPattern("/public/storybook/**"));
    @SuppressWarnings("unchecked")
    List<ViewControllerRegistration> registrations =
        (List<ViewControllerRegistration>) ReflectionTestUtils.getField(views, "registrations");
    ViewControllerRegistration storybook =
        registrations.stream()
            .filter(registration -> "/public/storybook".equals(urlPath(registration)))
            .findFirst()
            .orElseThrow();
    assertEquals("redirect:/public/storybook/index.html", viewName(storybook));
  }

  @Test
  void doesNotRegisterStorybookRouteWhenDeploymentPropertyIsDisabled() {
    WebConfig config = configWithStorybookEnabled(false);
    ResourceHandlerRegistry resources =
        new ResourceHandlerRegistry(new StaticApplicationContext(), new MockServletContext());

    config.addResourceHandlers(resources);

    assertFalse(resources.hasMappingForPattern("/public/storybook/**"));
  }

  private WebConfig configWithStorybookEnabled(boolean storybookEnabled) {
    WebConfig config = new WebConfig();
    ReflectionTestUtils.setField(
        config, "storybookPreviewEnabled", Boolean.toString(storybookEnabled));
    return config;
  }

  private String urlPath(ViewControllerRegistration registration) {
    return ReflectionTestUtils.invokeMethod(registration, "getUrlPath");
  }

  private String viewName(ViewControllerRegistration registration) {
    Object controller = ReflectionTestUtils.invokeMethod(registration, "getViewController");
    return ReflectionTestUtils.invokeMethod(controller, "getViewName");
  }
}
