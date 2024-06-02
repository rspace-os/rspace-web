package com.researchspace.linkedelements;

import com.researchspace.dao.AuditDao;
import com.researchspace.dao.GenericDao;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.record.BaseRecord;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;

/** Base class for converters of Text field element links to the entities that they refer to */
@Slf4j
class AbstractFieldElementConverter {

  private @Autowired AuditDao auditDao;

  protected void logNotFound(String type, long id) {
    log.warn("{} element with id: {} could not be retrieved", type, id);
  }

  protected long getFieldIdFromComposition(Element el) {
    if (el.hasAttr("id")) {
      String[] ids = el.attr("id").split("-");
      if (ids.length > 1) {
        try {
          return Long.parseLong(ids[0]);
        } catch (NumberFormatException nfe) {
          log.warn("unparsable composite id: " + el.attr("id"));
        }
      }
    }
    return -1;
  }

  protected long getElementIdFromComposition(Element el) {
    if (el.hasAttr("id")) {
      String[] ids = el.attr("id").split("-");
      if (ids.length > 1) {
        try {
          return Long.parseLong(ids[1]);
        } catch (NumberFormatException nfe) {
          log.warn("unparsable composite id: " + el.attr("id"));
        }
      }
    }
    return -1;
  }

  protected <T> Optional<T> getItem(
      Long id, Long revision, Class<T> clazz, GenericDao<T, Long> dao) {
    Optional<T> latestOptional = dao.getSafeNull(id);
    T entity = latestOptional.orElse(null);
    if (entity != null && revision != null) {
      AuditedEntity<T> objectForRevision = auditDao.getObjectForRevision(clazz, id, revision);
      if (objectForRevision == null) {
        return Optional.empty();
      }
      T entityRevision = objectForRevision.getEntity();
      if (entityRevision instanceof BaseRecord) {
        ((BaseRecord) entityRevision).setParents(((BaseRecord) entity).getParents());
      }
      entity = entityRevision;
    }
    return Optional.ofNullable(entity);
  }

  protected Long getRevisionFromElem(Element el) {
    String revisionAttr = el.attr("data-rsrevision");
    return StringUtils.isEmpty(revisionAttr) ? null : Long.valueOf(revisionAttr);
  }
}
