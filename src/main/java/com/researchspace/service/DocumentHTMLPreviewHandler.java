package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import org.apache.shiro.authz.AuthorizationException;

/**
 * Generates an HTML preview of a document. This provides an intermediate layer between controller
 * and the HTMLGenerator implementation to allow for caching.
 */
public interface DocumentHTMLPreviewHandler {

  /**
   * @param docId ID of a {@link StructuredDocument}
   * @param subject The current user
   * @return An HTML String of document, if permitted
   * @throws AuthorizationException if access not allowed.
   */
  DocHtmlPreview generateHtmlPreview(Long docId, User subject);
}
