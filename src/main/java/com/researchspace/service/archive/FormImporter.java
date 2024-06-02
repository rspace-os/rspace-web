package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.model.User;
import com.researchspace.model.record.RSForm;

/** Handles importing a form from an XML archive into database as a new form. */
public interface FormImporter {
  /**
   * @param parserRef
   * @param user
   * @return
   */
  RSForm makeRSForm(ArchivalDocumentParserRef parserRef, User user);
}
