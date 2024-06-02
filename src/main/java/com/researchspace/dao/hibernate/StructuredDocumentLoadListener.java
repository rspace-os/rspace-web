package com.researchspace.dao.hibernate;

import com.researchspace.model.record.StructuredDocument;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;

/**
 * When a {@link StructuredDocument} is loaded, this listener clears the record of auditable changes
 * that were last saved in the database.
 */
public class StructuredDocumentLoadListener implements PostLoadEventListener {

  /** */
  private static final long serialVersionUID = -2435415482899540624L;

  /**
   * Any changes to entity are flushed and persisted, as this method is still called from within
   * managed hibernate session.
   */
  @Override
  public void onPostLoad(PostLoadEvent event) {
    if (event.getEntity() instanceof StructuredDocument) {
      StructuredDocument doc = (StructuredDocument) event.getEntity();
      doc.clearDelta();
    }
  }
}
