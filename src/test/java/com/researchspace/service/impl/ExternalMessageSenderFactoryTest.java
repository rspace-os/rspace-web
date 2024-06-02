package com.researchspace.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.model.apps.App;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ExternalMessageSenderFactoryTest {
  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock ExternalMessageSender sender;
  List<ExternalMessageSender> senders = new ArrayList<>();
  ExternalMessageSenderFactoryImpl factory;

  @Before
  public void setUp() throws Exception {
    senders.add(sender);
    factory = new ExternalMessageSenderFactoryImpl();
    factory.setMessageSenders(senders);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testFindMessageSenderForApp() {
    App app = new App("name", "label", true);
    Mockito.when(sender.supportsApp(app)).thenReturn(false);
    assertFalse(factory.findMessageSenderForApp(app).isPresent());

    Mockito.when(sender.supportsApp(app)).thenReturn(true);
    assertTrue(factory.findMessageSenderForApp(app).isPresent());
  }
}
