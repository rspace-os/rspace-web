package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.field.Field;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.Query;

/** Sets parentids into thumnails */
public class ThumbnailAddParentIds extends AbstractCustomLiquibaseUpdater {

  @Override
  public String getConfirmationMessage() {
    return "Thumbnail parent ids set to fields";
  }

  @Override
  protected void doExecute(Database database) {
    List<Field> fields = fMger.findByTextContent(FieldParserConstants.CHEM_IMG_CLASSNAME);
    logger.info(fields.size() + " fields with attachments found to add");
    for (Field field : fields) {
      logger.info("searching " + field.getId());
      FieldContents contents = fieldParser.findFieldElementsInContent(field.getFieldData());

      logger.info("found  " + contents.getElements(RSChemElement.class).size() + " chem elements");
      for (RSChemElement chem : contents.getElements(RSChemElement.class).getElements()) {
        Long sourceId = chem.getId();
        Query q =
            sessionFactory
                .getCurrentSession()
                .createSQLQuery(
                    "update Thumbnail set sourceParentId =:fieldId where sourceId =:chemId and"
                        + " sourceParentId is NULL");
        q.setParameter("fieldId", field.getId());
        q.setParameter("chemId", sourceId);
        int updated = q.executeUpdate();
        if (updated <= 0) {
          logger.warn("Unable to updated  chem {} in field {}", sourceId, field.getId());
        }
      }
    }
  }
}
