package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.field.Field;
import java.util.List;
import liquibase.database.Database;

/** Connects documents to fields so that we can check permissions */
public class AddLinksToDocumentsInFields extends AbstractCustomLiquibaseUpdater {

  @Override
  public String getConfirmationMessage() {
    return "Document attachments linked to fields";
  }

  protected void doExecute(Database database) {
    List<Field> fields = fMger.findByTextContent(FieldParserConstants.ATTACHMENT_CLASSNAME);
    logger.info(fields.size() + " fields with attachments found to add");
    for (Field field : fields) {
      logger.info("searching " + field.getId());
      try {
        FieldContents contents = fieldParser.findFieldElementsInContent(field.getFieldData());

        logger.info(
            "found  " + contents.getElements(EcatDocumentFile.class).size() + " attachments");
        for (FieldElementLinkPair<EcatDocumentFile> edf :
            contents.getElements(EcatDocumentFile.class).getPairs()) {
          // we're running this at application start up so need to suspend permissions check
          fMger.addMediaFileLink(edf.getElement().getId(), null, field.getId(), true);
        }
        // we need to catch all exceptions, so we don't break all updates if only one fails
      } catch (Exception e) {
        logger.warn("Error in field id [{}]: {}", field.getId(), e.getMessage());
      }
    }
  }
}
