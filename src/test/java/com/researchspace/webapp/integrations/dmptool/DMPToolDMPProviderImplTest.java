package com.researchspace.webapp.integrations.dmptool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.researchspace.dmptool.client.DMPToolClient;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class DMPToolDMPProviderImplTest {

  private static final String PLAN_ID = "123";
  private static final String TOKEN = "token";
  private static final String EDIT_LINK = "https://datadryad.org/edit/doi%3A10.1/secretCode";

  private @Mock DMPToolClient client;
  private DMPToolDMPProviderImpl provider;
  private final EventCapture logs = new EventCapture();
  private final Logger logger = (Logger) LogManager.getLogger(DMPToolDMPProviderImpl.class);

  @BeforeEach
  void setUp() throws Exception {
    provider = new DMPToolDMPProviderImpl(new URL("https://dmptool.example.org"));
    ReflectionTestUtils.setField(provider, "dmpToolClient", client);
    ReflectionTestUtils.setField(
        provider, "messages", new MessageSourceUtils(new JsonMessageSource()));
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    logger.removeAppender(logs);
    logs.stop();
  }

  @Test
  void refusedUpdateGivesShortMessageWithStatusAndWarnsWithoutTheValue() throws Exception {
    doThrow(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                new HttpHeaders(),
                ("echo " + EDIT_LINK).getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8))
        .when(client)
        .postRelatedIdentifiers(eq(PLAN_ID), any(), eq(TOKEN));

    ServiceOperationResult<String> result =
        provider.addRelatedIdentifierToDMP(PLAN_ID, "url", EDIT_LINK, TOKEN);

    assertFalse(result.isSucceeded());
    assertEquals("DMPTool refused the update (error 404)", result.getEntity());
    assertEquals(1, logs.events.size());
    LogEvent warn = logs.events.get(0);
    assertEquals(Level.WARN, warn.getLevel());
    assertThat(warn.getMessage().getFormattedMessage())
        .contains(PLAN_ID, "404")
        .doesNotContain(EDIT_LINK, "secretCode");
  }

  @Test
  void unreachableDmpToolGivesShortMessageAndWarnsWithoutTheValue() throws Exception {
    doThrow(new ResourceAccessException("I/O error on POST request for " + EDIT_LINK))
        .when(client)
        .postRelatedIdentifiers(eq(PLAN_ID), any(), eq(TOKEN));

    ServiceOperationResult<String> result =
        provider.addRelatedIdentifierToDMP(PLAN_ID, "url", EDIT_LINK, TOKEN);

    assertFalse(result.isSucceeded());
    assertEquals("DMPTool could not be reached", result.getEntity());
    assertEquals(1, logs.events.size());
    assertEquals(Level.WARN, logs.events.get(0).getLevel());
    assertThat(logs.events.get(0).getMessage().getFormattedMessage())
        .contains(PLAN_ID)
        .doesNotContain(EDIT_LINK, "secretCode");
  }

  @Test
  void nonHttpErrorGivesUnreachableMessage() throws Exception {
    doThrow(new URISyntaxException(EDIT_LINK, "bad"))
        .when(client)
        .postRelatedIdentifiers(eq(PLAN_ID), any(), eq(TOKEN));

    ServiceOperationResult<String> result =
        provider.addRelatedIdentifierToDMP(PLAN_ID, "url", EDIT_LINK, TOKEN);

    assertEquals("DMPTool could not be reached", result.getEntity());
  }

  private static class EventCapture extends AbstractAppender {
    final List<LogEvent> events = new ArrayList<>();

    EventCapture() {
      super("dmpToolProviderCapture", null, null, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }
  }
}
