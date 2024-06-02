package com.researchspace.dao.customliquibaseupdates.v47;

import com.researchspace.dao.FieldDao;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.model.field.TextField;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

/**
 * With RSPAC-1053 new filestore links have mceNonEditable class. This liquibase update will add the
 * class to pre-existing links.
 */
public class UpdateFilestoreLinksInFieldContent extends AbstractCustomLiquibaseUpdater {

  private FieldDao fieldDao;

  public static final String OLD_LINK_MATCHER = "<a class=\"nfs_file\"";
  private static final String NEW_LINK_MATCHER = "<a class=\"nfs_file mceNonEditable\"";

  private int fieldsWithLinksCounter = 0;
  private int updatedFieldsCounter = 0;

  @Override
  protected void addBeans() {
    fieldDao = context.getBean(FieldDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Filestore links updated in "
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
    logger.info("filestore link class update finished fine");
  }

  protected List<TextField> getAllTextFieldsWithLinks() {
    Criteria criteria = sessionFactory.getCurrentSession().createCriteria(TextField.class);
    criteria.add(Restrictions.like("rtfData", "%" + OLD_LINK_MATCHER + "%"));
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    return criteria.list();
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
