package com.researchspace.dao.customliquibaseupdates.v29;

import com.researchspace.dao.EcatCommentDao;
import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldElementLinkPairs;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RecordAttachment;
import com.researchspace.model.record.Snippet;
import java.util.List;
import java.util.Set;
import liquibase.database.Database;
import org.hibernate.Criteria;

/**
 * As raised in RSPAC-689 entities inside snippets were not properly linked: 1)
 * comments/annotations/chemicals where pointing to snippet.id through their parentId, but that
 * property should really hold field.id (and snippets don't have field) 2) EcatMediaFiles
 * (images/audio/video) were not linked at all
 *
 * <p>This update is going to fix the situation by iterating over all existing snippets and: 1)
 * parsing their content to find all entities in it 2) for comments/annotations/chemicals in content
 * the parentId will be set to null, and record_id will be set to snippet.id 3) for all
 * EcatMediaFiles in content the RecordAttachment entity will be created
 */
public class UpdateReferencesInSnippetAttachments extends AbstractCustomLiquibaseUpdater {

  private RecordDao recordDao;

  private EcatCommentDao commentDao;

  private EcatImageAnnotationDao imageAnnotationDao;

  private RSChemElementDao chemElementDao;

  private int allSnippetsCounter = 0;
  private int updatedSnippetsCounter = 0;

  @Override
  protected void addBeans() {
    recordDao = context.getBean("recordDao", RecordDao.class);
    commentDao = context.getBean("ecatCommentDao", EcatCommentDao.class);
    imageAnnotationDao = context.getBean("ecatImageAnnotationDao", EcatImageAnnotationDao.class);
    chemElementDao = context.getBean("RSChemElementDaoHibernate", RSChemElementDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Linking snippet attachments complete, "
        + updatedSnippetsCounter
        + "/"
        + allSnippetsCounter
        + " snippets updated";
  }

  @Override
  protected void doExecute(Database database) {
    logger.info("executing liquibase update");

    // get all snippets
    List<Snippet> existingSnippets = getAllSnippets();
    allSnippetsCounter = existingSnippets.size();

    boolean elementsInSnippetUpdated;
    boolean recordAttachmentsUpdated;

    // update them one by one
    for (Snippet snip : existingSnippets) {

      FieldContents snipContents = fieldParser.findFieldElementsInContent(snip.getContent());
      elementsInSnippetUpdated = updateRelationsInCommentSketchChemicals(snipContents, snip);
      recordAttachmentsUpdated = updateRecordAttachmentsForMediaFiles(snipContents, snip);

      if (elementsInSnippetUpdated || recordAttachmentsUpdated) {
        updatedSnippetsCounter++;
      }
    }

    logger.info(
        "updated " + updatedSnippetsCounter + "out of " + allSnippetsCounter + " snippet(s)");
    logger.info("snippet attachment linking finished fine");
  }

  List<Snippet> getAllSnippets() {
    Criteria criteria = sessionFactory.getCurrentSession().createCriteria(Snippet.class);
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    return criteria.list();
  }

  private boolean updateRelationsInCommentSketchChemicals(FieldContents contents, Snippet snip) {

    boolean anythingSaved = false;
    boolean saveCurrentElement = false;

    FieldElementLinkPairs<EcatComment> comments = contents.getElements(EcatComment.class);
    if (comments != null) {
      for (EcatComment comm : comments.getElements()) {

        saveCurrentElement = false;
        if (snip.getId().equals(comm.getParentId())) {
          comm.setParentId(null);
        }
        if (comm.getRecord() == null) {
          comm.setRecord(snip);
        }
        if (saveCurrentElement) {
          commentDao.save(comm);
          anythingSaved = true;
        }
      }
    }

    FieldElementLinkPairs<EcatImageAnnotation> annotationsAndSketches =
        new FieldElementLinkPairs<>(EcatImageAnnotation.class);
    annotationsAndSketches.addAll(contents.getImageAnnotations().getPairs());
    annotationsAndSketches.addAll(contents.getSketches().getPairs());
    for (EcatImageAnnotation anno : annotationsAndSketches.getElements()) {

      saveCurrentElement = false;
      if (snip.getId().equals(anno.getParentId())) {
        anno.setParentId(null);
      }
      if (anno.getRecord() == null) {
        anno.setRecord(snip);
      }
      if (saveCurrentElement) {
        imageAnnotationDao.save(anno);
        anythingSaved = true;
      }
    }

    FieldElementLinkPairs<RSChemElement> chemElements = contents.getElements(RSChemElement.class);
    if (comments != null) {
      for (RSChemElement chem : chemElements.getElements()) {
        saveCurrentElement = false;
        if (snip.getId().equals(chem.getParentId())) {
          chem.setParentId(null);
        }
        if (chem.getRecord() == null) {
          chem.setRecord(snip);
        }
        if (saveCurrentElement) {
          chemElementDao.save(chem);
          anythingSaved = true;
        }
      }
    }

    return anythingSaved;
  }

  private boolean updateRecordAttachmentsForMediaFiles(FieldContents contents, Snippet snip) {

    FieldElementLinkPairs<EcatMediaFile> mediaInContent = contents.getAllMediaFiles();
    Set<RecordAttachment> alreadyLinked = snip.getLinkedMediaFiles();
    boolean saveSnippet = false;

    for (EcatMediaFile media : mediaInContent.getElements()) {
      RecordAttachment newRecordAttachment = new RecordAttachment(snip, media);
      if (!alreadyLinked.contains(newRecordAttachment)) {
        snip.getLinkedMediaFiles().add(newRecordAttachment);
        saveSnippet = true;
      }
    }
    if (saveSnippet) {
      recordDao.save(snip);
    }
    return saveSnippet;
  }
}
