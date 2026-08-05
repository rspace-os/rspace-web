package com.researchspace.search.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.researchspace.model.record.StructuredDocument;
import com.researchspace.search.impl.TemporaryDocRoutingBinder.TemporaryDocRoutingBridge;
import com.researchspace.testutils.TestFactory;
import org.hibernate.search.mapper.pojo.route.DocumentRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TemporaryDocRoutingBinderTest {

  private final TemporaryDocRoutingBridge bridge = new TemporaryDocRoutingBridge();
  private DocumentRoutes routes;
  private StructuredDocument doc;

  @BeforeEach
  public void setUp() {
    routes = Mockito.mock(DocumentRoutes.class);
    doc = TestFactory.createAnySD();
    doc.setId(1L);
  }

  @Test
  public void normalDocumentIsIndexed() {
    bridge.route(routes, doc.getId(), doc, null);

    verify(routes).addRoute();
    verifyNoMoreInteractions(routes);
  }

  @Test
  public void temporaryDocumentIsNotIndexed() {
    doc.setTemporaryDoc(true);
    bridge.route(routes, doc.getId(), doc, null);

    verify(routes).notIndexed();
    verifyNoMoreInteractions(routes);
  }

  @Test
  public void previousRoutesAlwaysIncludeDefaultRouteSoStaleEntriesGetDeleted() {
    doc.setTemporaryDoc(true);
    bridge.previousRoutes(routes, doc.getId(), doc, null);

    verify(routes).addRoute();
    verifyNoMoreInteractions(routes);
  }
}
