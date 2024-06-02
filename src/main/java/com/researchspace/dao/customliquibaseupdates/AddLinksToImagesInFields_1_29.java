package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.model.field.Field;
import java.util.List;
import liquibase.database.Database;

/** Connects documents to fields so that we can check permissions */
public class AddLinksToImagesInFields_1_29 extends AbstractCustomLiquibaseUpdater {

  @Override
  public String getConfirmationMessage() {
    return "Document attachments linked to fields";
  }

  protected void doExecute(Database database) {
    List<Field> fields = fMger.findByTextContent(FieldParserConstants.IMAGE_DROPPED_CLASS_NAME);
    logger.info(fields.size() + " fields with attachments found to add");
    //		for (Field field: fields) {
    //			logger.info("searching " + field.getId());
    //			try {
    //				FieldContents contents = fieldParser.findFieldElementsInContent(field.getFieldData());
    //
    //				logger.info("found  " + contents.getImages().size() + " images");
    //
    //				for (EcatImage img : contents.getImages()) {
    //					boolean isFound = false;
    //					for (FieldAttachment linked:  field.getLinkedMediaFiles()) {
    //						if (linked.getMediaFile().equals(img)) {
    //							isFound = true;
    //							break;
    //						}
    //					}
    //					if(!isFound) {
    //						logger.warn(" No entry in field_attachments table  found for image {} in field {} ",
    // img.getId(), field.getId());
    //					}
    //				}
    //			// we need to catch all exceptions, so we don't break all updates if only one fails
    //			} catch (Exception e)  {
    //				logger.warn("Error in field id [{}]: {}",field.getId(), e.getMessage());
    //			}
    //		}
  }
}
