package com.researchspace.webapp.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.webapp.controller.IgnoreInLoggingInterceptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** OAuth callbacks receive authorization codes and state, which must stay out of request logs. */
class OAuthCallbackLoggingTest {

  @Test
  void everyOAuthCallbackOmitsItsRequestParamsFromLogs() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
    List<String> callbacks = new ArrayList<>();
    List<String> unprotected = new ArrayList<>();
    for (BeanDefinition bean : scanner.findCandidateComponents(getClass().getPackageName())) {
      for (Method method : Class.forName(bean.getBeanClassName()).getDeclaredMethods()) {
        RequestMapping mapping =
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null
            || Stream.concat(Arrays.stream(mapping.value()), Arrays.stream(mapping.path()))
                .noneMatch(
                    path ->
                        path.endsWith("/redirect_uri")
                            || path.endsWith("/callback")
                            || path.startsWith("/callbacks/"))) {
          continue;
        }
        String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        callbacks.add(name);
        IgnoreInLoggingInterceptor ignore = method.getAnnotation(IgnoreInLoggingInterceptor.class);
        if (ignore == null || !ignore.ignoreAllRequestParams()) {
          unprotected.add(name);
        }
      }
    }
    assertThat(callbacks)
        .contains(
            "GitHubController.onAuthorization",
            "SlackController.basicSearch",
            "SlackController.saveConversation");
    assertThat(unprotected).isEmpty();
  }
}
