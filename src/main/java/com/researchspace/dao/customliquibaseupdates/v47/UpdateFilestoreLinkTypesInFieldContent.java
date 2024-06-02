package com.researchspace.dao.customliquibaseupdates.v47;

import com.researchspace.dao.FieldDao;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.model.field.TextField;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.query.Query;

/**
 * With RSPAC-1354 we add possibility to link to folders. We should add a 'data' attribute to the
 * pre-existing links that will describe them as links to files.
 */
public class UpdateFilestoreLinkTypesInFieldContent extends AbstractCustomLiquibaseUpdater {

  private FieldDao fieldDao;

  public static final String OLD_LINK_MATCHER = "<a class=\"nfs_file mceNonEditable\"";
  private static final String NEW_LINK_MATCHER =
      "<a class=\"nfs_file mceNonEditable\" data-linktype=\"file\"";

  private int fieldsWithLinksCounter = 0;
  private int updatedFieldsCounter = 0;

  @Override
  protected void addBeans() {
    fieldDao = context.getBean(FieldDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Filestore link types updated in "
        + updatedFieldsCounter
        + "/"
        + fieldsWithLinksCounter
        + " fields.";
  }

  @Override
  protected void doExecute(Database database) {
    logger.info("executing liquibase update");

    // get all textFields
    List<TextField> textFieldsWithLinks = getAllTextFieldsWithLinks();
    fieldsWithLinksCounter = textFieldsWithLinks.size();

    // update them one by one
    for (TextField tf : textFieldsWithLinks) {
      updateLinksInTextField(tf);
    }

    logger.info(
        "updated "
            + updatedFieldsCounter
            + " out of "
            + fieldsWithLinksCounter
            + " identified fields");
    logger.info("filestore link types update finished fine");
  }

  protected List<TextField> getAllTextFieldsWithLinks() {
    Query<TextField> query =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from TextField where rtfData  like '%" + OLD_LINK_MATCHER + "%'", TextField.class);
    return query.list();
  }

  private void updateLinksInTextField(TextField tf) {

    String fieldData = tf.getFieldData();
    String updatedFieldData = fieldData;

    updatedFieldData = fieldData.replace(OLD_LINK_MATCHER, NEW_LINK_MATCHER);

    if (!fieldData.equals(updatedFieldData)) {
      tf.setData(updatedFieldData);
      updatedFieldsCounter++;
      fieldDao.save(tf);
    }
  }
}
