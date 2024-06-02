package com.researchspace.dao.hibernate;

import com.researchspace.dao.InternalLinkDao;
import com.researchspace.model.InternalLink;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Repository;

/** For manipulating internal link entities. */
@Repository("internalLinkDao")
public class InternalLinkDaoHibernate implements InternalLinkDao {

  private SessionFactory sessionFactory;

  @Autowired
  @Required
  public void setSessionFactory(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public List<InternalLink> getLinksPointingToRecord(long targetRecordId) {
    List<InternalLink> links =
        sessionFactory
            .getCurrentSession()
            .createQuery("from InternalLink where target_id=:targetRecordId", InternalLink.class)
            .setParameter("targetRecordId", targetRecordId)
            .list();
    List<InternalLink> linksWhereSourceNotDeleted =
        links.stream().filter(il -> !il.getSource().isDeleted()).collect(Collectors.toList());
    return linksWhereSourceNotDeleted;
  }

  @Override
  public List<InternalLink> getLinksFromRecordContent(long sourceRecordId) {
    List<InternalLink> links =
        sessionFactory
            .getCurrentSession()
            .createQuery("from InternalLink where source_id=:sourceRecordId", InternalLink.class)
            .setParameter("sourceRecordId", sourceRecordId)
            .list();
    return links;
  }

  @Override
  public boolean saveInternalLink(long sourceRecordId, long targetRecordId) {
    InternalLink existingLink = getInternalLinkBySourceAndTarget(sourceRecordId, targetRecordId);
    if (existingLink == null) {
      Session ss = sessionFactory.getCurrentSession();
      Record source = ss.load(Record.class, sourceRecordId);
      BaseRecord target = ss.load(BaseRecord.class, targetRecordId);
      ss.merge(new InternalLink(source, target));
      return true;
    }
    return false;
  }

  @Override
  public void deleteInternalLink(long sourceRecordId, long targetRecordId) {
    InternalLink existingLink = getInternalLinkBySourceAndTarget(sourceRecordId, targetRecordId);
    if (existingLink != null) {
      Session ss = sessionFactory.getCurrentSession();
      ss.delete(existingLink);
    }
  }

  private InternalLink getInternalLinkBySourceAndTarget(long sourceRecordId, long targetRecordId) {
    InternalLink existingLink =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                " from InternalLink where source_id=:sourceRecordId and target_id=:targetRecordId",
                InternalLink.class)
            .setParameter("sourceRecordId", sourceRecordId)
            .setParameter("targetRecordId", targetRecordId)
            .uniqueResult();
    return existingLink;
  }
}
