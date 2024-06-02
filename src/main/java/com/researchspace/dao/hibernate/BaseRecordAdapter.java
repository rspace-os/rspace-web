package com.researchspace.dao.hibernate;

import static com.researchspace.core.util.TransformerUtils.toSet;

import com.researchspace.dao.EcatCommentDao;
import com.researchspace.dao.FieldDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.ThumbnailDao;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.RecordAttachment;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps objects associated with documents (comments, chem elements etc) returned from a Lucene-based
 * text search, or for a permissions look-up to BaseRecord objects.
 *
 * <p><strong> If renaming this class, remember to edit the transactional class definition in
 * applicationService.xml
 *
 * <p></strong>
 *
 * <p>
 */
@Component
@Transactional
// so can be used in controllers to check object permissions
public class BaseRecordAdapter implements BaseRecordAdaptable {

  private @Autowired ThumbnailDao thumbdao;
  private @Autowired EcatCommentDao commentDao;

  private RecordDao recordDao;

  @Autowired
  public void setRecordDao(RecordDao recordDao) {
    this.recordDao = recordDao;
  }

  private FieldDao fieldDao;

  @Autowired
  public void setFieldDao(FieldDao fieldDao) {
    this.fieldDao = fieldDao;
  }

  // this will cache based on the globalid of the element being looked up.
  // cache policy is defined in ehcache.xml.
  // this will lookupin cache if the object's getId() method isn't null. This
  // is to avoid
  // a NPE being thrown out of Spring EL (e.g., when inserting a new comment,
  // it doesn't yet have an id). Also, if toAdapt is itself a base record, we want to return this
  // object
  // , not a possible stale value from the cache.
  @Override
  @Cacheable(
      value = "com.researchspace.model.record.BaseRecord",
      key = "#a0.oid",
      condition =
          "(#a0.id ne null) and !(#a0 instanceof T(com.researchspace.model.record.BaseRecord))")
  public Optional<BaseRecord> getAsBaseRecord(IFieldLinkableElement toAdapt) {
    Set<BaseRecord> rc = getAsBaseRecord(toAdapt, false);
    if (rc.isEmpty()) {
      return Optional.empty();
    } else {
      BaseRecord doc = rc.iterator().next();
      // this prevents us keeping links to deleted items accessible
      if (!doc.isDeleted()) {
        return Optional.of(doc);
      } else {
        return Optional.empty();
      }
    }
  }

  public Set<BaseRecord> getAsBaseRecord(IFieldLinkableElement toAdapt, boolean isLinkedRecord) {
    if (BaseRecord.class.isAssignableFrom(toAdapt.getClass()) && !isLinkedRecord) {
      return toSet((BaseRecord) toAdapt);
    } else if (Field.class.isAssignableFrom(toAdapt.getClass())) {
      BaseRecord doc = ((Field) toAdapt).getStructuredDocument();
      return toSet(doc);
    } else if (EcatComment.class.isAssignableFrom(toAdapt.getClass())) {
      return toSet(findLinkedRecordFromComment(toAdapt));
    } else if (EcatCommentItem.class.isAssignableFrom(toAdapt.getClass())) {
      return toSet(findLinkedRecordFromCommentItem(toAdapt));
    } else if (EcatImageAnnotation.class.isAssignableFrom(toAdapt.getClass())) {
      return toSet(findLinkedRecordFromAnnotation(toAdapt));
    } else if (RSChemElement.class.isAssignableFrom(toAdapt.getClass())) {
      return toSet(findRecordLinkedtoChemElement(toAdapt));
    } else if (RSMath.class.isAssignableFrom(toAdapt.getClass())) {
      return toSet(findRecordLinkedtoMath(toAdapt));
    } else if (BaseRecord.class.isAssignableFrom(toAdapt.getClass()) && isLinkedRecord) {
      return findLinkedRecord(toAdapt);
    } else if (Thumbnail.class.isAssignableFrom(toAdapt.getClass())) {
      return toSet(getThumbnailLinkedDoc(toAdapt));
    }
    return Collections.EMPTY_SET;
  }

  private BaseRecord getThumbnailLinkedDoc(Object toAdapt) {
    Thumbnail tn = (Thumbnail) toAdapt;
    // for backwards compatibility pre-0.24
    if (tn.getSourceParentId() == null) {
      tn = thumbdao.get(tn.getId());
    }
    return getRecordFromItem(tn.getSourceParentId());
  }

  private BaseRecord findLinkedRecordFromAnnotation(Object toAdapt) {
    EcatImageAnnotation annot = (EcatImageAnnotation) toAdapt;
    if (annot.getRecord() != null) {
      annot.getRecord().getOwner().getUsername(); // initialise hibernate proxy
      return annot.getRecord();
    }
    return getRecordFromItem(annot.getParentId());
  }

  private BaseRecord findLinkedRecordFromComment(Object toAdapt) {
    EcatComment comment = (EcatComment) toAdapt;
    if (comment.getRecord() != null) {
      return comment.getRecord();
    }
    return getRecordFromItem(comment.getParentId());
  }

  private BaseRecord findLinkedRecordFromCommentItem(Object toAdapt) {
    EcatCommentItem ci = (EcatCommentItem) toAdapt;
    EcatComment comm = ci.getEcatComment();
    if (comm == null) {
      comm = commentDao.getEcatComment(ci.getComId(), null);
    }
    return findLinkedRecordFromComment(comm);
  }

  private BaseRecord findRecordLinkedtoChemElement(Object toAdapt) {
    RSChemElement rsChemElement = (RSChemElement) toAdapt;
    if (rsChemElement.getRecord() != null) {
      return rsChemElement.getRecord();
    }
    return getRecordFromItem(rsChemElement.getParentId());
  }

  private BaseRecord findRecordLinkedtoMath(IFieldLinkableElement toAdapt) {
    RSMath math = (RSMath) toAdapt;
    if (math.getRecord() != null) {
      return math.getRecord();
    } else {
      return math.getField().getStructuredDocument();
    }
  }

  private BaseRecord getRecordFromItem(long parentId) {
    Field field = null;
    try {
      field = fieldDao.get(parentId);
      return getDocFromField(field);
    } catch (DataAccessException ex) {
      // maybe is a snippet
      return recordDao.get(parentId);
    }
  }

  private BaseRecord getDocFromField(Field f) {
    return f != null ? f.getStructuredDocument() : null;
  }

  private Set<BaseRecord> findLinkedRecord(Object toAdapt) {
    BaseRecord br = (BaseRecord) toAdapt;
    if (!br.isMediaRecord()) {
      return Collections.EMPTY_SET;
    }

    EcatMediaFile media = (EcatMediaFile) br;
    media = (EcatMediaFile) recordDao.get(media.getId());
    Set<BaseRecord> rc = new HashSet<>();
    for (FieldAttachment fieldAttachment : media.getLinkedFields()) {
      rc.add(fieldAttachment.getField().getStructuredDocument());
    }
    for (RecordAttachment recordAttachment : media.getLinkedRecords()) {
      rc.add(recordAttachment.getRecord());
    }
    return rc;
  }
}
