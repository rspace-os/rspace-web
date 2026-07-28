package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.model.apps.App;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExternalMessageSenderFactoryTest {
  @Mock ExternalMessageSender sender;
  List<ExternalMessageSender> senders = new ArrayList<>();
  ExternalMessageSenderFactoryImpl factory;

  @BeforeEach
  public void setUp() throws Exception {
    senders.add(sender);
    factory = new ExternalMessageSenderFactoryImpl();
    factory.setMessageSenders(senders);
  }

  @Test
  public void testFindMessageSenderForApp() {
    App app = new App("name", "label", true);
    Mockito.when(sender.supportsApp(app)).thenReturn(false);
    assertFalse(factory.findMessageSenderForApp(app).isPresent());

    Mockito.when(sender.supportsApp(app)).thenReturn(true);
    assertTrue(factory.findMessageSenderForApp(app).isPresent());
  }
}
