package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.ThumbnailDao;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.Thumbnail.SourceType;
import java.util.List;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Example;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository("thumbnailDao")
public class ThumbnailDAOHibernate extends GenericDaoHibernate<Thumbnail, Long>
    implements ThumbnailDao {

  public ThumbnailDAOHibernate() {
    super(Thumbnail.class);
  }

  public Thumbnail getThumbnail(Thumbnail example) {
    Session session = getSessionFactory().getCurrentSession();

    Example eg = Example.create(example);
    Criteria crit = session.createCriteria(Thumbnail.class);
    crit.add(eg);

    @SuppressWarnings("unchecked")
    List<Thumbnail> lst = crit.list();

    // Should never be more than one match for this method
    if (lst == null || lst.size() == 0) {
      return null;
    } else {
      return lst.get(0);
    }
  }

  public int deleteAllThumbnails(SourceType type, Long sourceId) {
    return deleteAllThumbnails(type, sourceId, null, null);
  }

  public int deleteAllThumbnails(SourceType type, Long sourceId, Long sourceParentId) {
    return deleteAllThumbnails(type, sourceId, sourceParentId, null);
  }

  public int deleteAllThumbnails(
      SourceType type, Long sourceId, Long sourceParentId, Long revision) {
    Session session = getSession();

    Query<?> q = null;
    if (sourceParentId == null) {
      // Delete all thumbnails with a matching source id
      q =
          session.createQuery(
              "delete Thumbnail where sourceType = :sourceType and sourceId = :sourceId");
    } else if (revision == null) {
      // Only delete thumbnails matching both source id and source parent
      // id
      q =
          session.createQuery(
              "delete Thumbnail where sourceType = :sourceType "
                  + "and sourceId = :sourceId and sourceParentId = :sourceParentId");
      q.setParameter("sourceParentId", sourceParentId);
    } else {
      // Only delete thumbnails matching source id, source parent id, and
      // revision id
      q =
          session.createQuery(
              "delete Thumbnail where sourceType = :sourceType "
                  + "and sourceId = :sourceId and sourceParentId = :sourceParentId "
                  + "and revision = :revision");
      q.setParameter("sourceParentId", sourceParentId);
      q.setParameter("revision", revision);
    }
    q.setParameter("sourceType", type);
    q.setParameter("sourceId", sourceId);
    int deletedCount = q.executeUpdate();
    return deletedCount;
  }

  @Override
  public List<Thumbnail> getByFieldId(Long fieldId) {
    return getSession()
        .createQuery("from Thumbnail where sourceParentId=:fieldId", Thumbnail.class)
        .setParameter("fieldId", fieldId)
        .list();
  }
}
