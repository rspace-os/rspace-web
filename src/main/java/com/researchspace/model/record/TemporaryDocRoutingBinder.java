package com.researchspace.model.record;

import org.hibernate.search.mapper.pojo.bridge.RoutingBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.RoutingBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.RoutingBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.RoutingBridgeRouteContext;
import org.hibernate.search.mapper.pojo.route.DocumentRoutes;

/**
 * Keeps temporary autosave copies of documents out of the Lucene index.
 *
 * <p>Every edit session creates a working copy of the document being edited (see {@code
 * RecordManager#saveTemporaryDocument} in rspace-web) which shares the original's name, tags and
 * form. If indexed, such copies show up in workspace search as phantom duplicates: they have no
 * folder path, no fields and version 0, and operations like move fail on them. They are also
 * re-saved on every autosave, so excluding them avoids pointless index writes during editing.
 */
public class TemporaryDocRoutingBinder implements RoutingBinder {

  @Override
  public void bind(RoutingBindingContext context) {
    context.dependencies().use("temporaryDoc");
    context.bridge(StructuredDocument.class, new TemporaryDocRoutingBridge());
  }

  static class TemporaryDocRoutingBridge implements RoutingBridge<StructuredDocument> {

    @Override
    public void route(
        DocumentRoutes routes,
        Object entityIdentifier,
        StructuredDocument document,
        RoutingBridgeRouteContext context) {
      if (document.isTemporaryDoc()) {
        routes.notIndexed();
      } else {
        routes.addRoute();
      }
    }

    @Override
    public void previousRoutes(
        DocumentRoutes routes,
        Object entityIdentifier,
        StructuredDocument document,
        RoutingBridgeRouteContext context) {
      // The temporary flag never flips on a live entity, but always emitting the
      // default route means update/delete events clean up any stale index entry,
      // e.g. one written before this binder existed.
      routes.addRoute();
    }
  }
}
