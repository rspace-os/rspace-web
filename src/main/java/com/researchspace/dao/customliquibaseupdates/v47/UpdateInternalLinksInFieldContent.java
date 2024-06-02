package com.researchspace.dao.customliquibaseupdates.v47;

import com.researchspace.dao.FieldDao;
import com.researchspace.dao.InternalLinkDao;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.model.field.TextField;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.orm.ObjectRetrievalFailureException;

/**
 * With RSPAC-1343 we started tracking internal links between RSpace records. This liquibase update
 * will:
 *
 * <p>1) create InternalLink entities for currently existing links from RSpace documents
 *
 * <p>2) update html structure of the internal link to new format (with 'mceNonEditable' class and
 * data-globalid element). old format: <a id="21957" class="linkedRecord"
 * href="/workspace/editor/structuredDocument/21957" data-name="entry 1">entry 1</a> new format: <a
 * id="21957" data-globalid="SD21957" class="linkedRecord mceNonEditable" href="/globalId/SD21957"
 * data-name="entry 1">entry 1</a>
 */
public class UpdateInternalLinksInFieldContent extends AbstractCustomLiquibaseUpdater {

  private FieldDao fieldDao;
  private InternalLinkDao internalLinkDao;

  static final String OLD_LINK_MATCHER =
      "class=\"linkedRecord\" href=\"/workspace/editor/structuredDocument/";

  private int fieldsWithLinksCounter = 0;
  private int createdLinksCounter = 0;
  private int updatedFieldsCounter = 0;

  @Override
  protected void addBeans() {
    fieldDao = context.getBean(FieldDao.class);
    internalLinkDao = context.getBean(InternalLinkDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Internal links creation complete. "
        + createdLinksCounter
        + " links created, "
        + updatedFieldsCounter
        + "/"
        + fieldsWithLinksCounter
        + " fields updated.";
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
        "created "
            + createdLinksCounter
            + " links in DB, updated "
            + updatedFieldsCounter
            + " out of "
            + fieldsWithLinksCounter
            + " identified fields");
    logger.info("internal link creation finished fine");
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

    FieldContents elementsInContent = fieldParser.findFieldElementsInContent(fieldData);
    List<RecordInformation> linkedRecords =
        elementsInContent.getElements(RecordInformation.class).getElements();

    for (RecordInformation targetRecordInfo : linkedRecords) {
      if (targetRecordInfo == null) {
        continue;
      }
      StructuredDocument parentDoc = tf.getStructuredDocument();
      if (parentDoc != null) {
        /* parent doc doesn't exist for temporary fields, but that's ok as
         * for these fields the we can just update link format and wait until
         * user reopens and saves the doc */
        try {
          boolean linkCreated =
              internalLinkDao.saveInternalLink(parentDoc.getId(), targetRecordInfo.getId());
          if (linkCreated) {
            createdLinksCounter++;
          }
          updatedFieldData = updateLinkFormat(targetRecordInfo, updatedFieldData);
        } catch (ObjectRetrievalFailureException e) {
          String warnMsg =
              String.format(
                  "ObjectRetrievalFailureException when updating internal link "
                      + "from %d to %d. Skipping the link. ",
                  parentDoc.getId(), targetRecordInfo.getId());
          logger.warn(warnMsg);
        }
      }
    }

    if (!fieldData.equals(updatedFieldData)) {
      tf.setData(updatedFieldData);
      updatedFieldsCounter++;
      fieldDao.save(tf);
    }
  }

  private String updateLinkFormat(RecordInformation link, String fieldData) {
    String oldLink = OLD_LINK_MATCHER + link.getId() + "\"";
    String newLink =
        "data-globalid=\"SD"
            + link.getId()
            + "\" class=\"linkedRecord mceNonEditable\" "
            + "href=\"/globalId/SD"
            + link.getId()
            + "\"";
    return fieldData.replace(oldLink, newLink);
  }
}
