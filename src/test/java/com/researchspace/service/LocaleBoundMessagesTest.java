package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LocaleBoundMessagesTest {

  @Mock private MessageSourceUtils messages;

  @Test
  public void formatDelegatesWithBoundLocale() {
    Locale fr = Locale.FRENCH;
    LocaleBoundMessages bound = new LocaleBoundMessages(messages, fr);
    Mockito.when(messages.format("some.key", List.of("a"), fr)).thenReturn("resolved in french");

    assertEquals("resolved in french", bound.format("some.key", List.of("a")));
    verify(messages).format("some.key", List.of("a"), fr);
  }

  @Test
  public void getMessageDelegatesWithBoundLocale() {
    Locale de = Locale.GERMAN;
    LocaleBoundMessages bound = new LocaleBoundMessages(messages, de);
    Mockito.when(messages.getMessage("k", null, de)).thenReturn("de value");

    assertEquals("de value", bound.getMessage("k"));
    verify(messages).getMessage("k", null, de);
  }
}
