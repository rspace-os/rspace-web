package com.researchspace.dao.hibernate;

import com.researchspace.dao.EcatCommentDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import java.text.SimpleDateFormat;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

/*Implementation of ecatCommentDao
 * Each item method should be used with createTransction/closeSession
 * @Sunny
 */

@Repository("ecatCommentDao")
public class EcatCommentDaoHibernate extends GenericDaoHibernate<EcatComment, Long>
    implements EcatCommentDao {
  static final SimpleDateFormat dtFmt = new SimpleDateFormat("yyyy-MM-dd");

  public EcatCommentDaoHibernate() {
    super(EcatComment.class);
  }

  @Override
  public EcatComment addComment(EcatComment cm) {
    return save(cm); // cascade to all children
  }

  @Override
  public void addCommentItem(EcatComment cm, EcatCommentItem itm) {
    List<EcatCommentItem> lst = cm.getItems();
    itm.setComId(cm.getComId());
    lst.add(itm);
    getSession().save(itm);
  }

  @Override
  public List<EcatComment> getCommentAll(Long parentId) {
    Session session = getSession();
    NativeQuery<EcatComment> sq =
        session.createNativeQuery(
            "select * from ecat_comm where parent_id = :parentId  order by com_id",
            EcatComment.class);
    sq.setParameter("parentId", parentId);
    List<EcatComment> lst = sq.list();

    for (int i = 0; i < lst.size(); i++) {
      EcatComment ecm = (EcatComment) lst.get(i);
      List<EcatCommentItem> lst1 = getCommentItems(ecm.getComId());
      ecm.setItems(lst1);
    }
    return lst;
  }

  @Override
  public EcatComment getEcatComment(Long comId, Long parentId) {
    // createTransaction();
    Session session = getSession();
    NativeQuery<EcatComment> sq =
        session.createNativeQuery(
            "select * from ecat_comm where com_id = :comId order by com_id", EcatComment.class);
    sq.setParameter("comId", comId);
    List<EcatComment> lst = sq.list();
    if (lst == null || lst.size() < 1) {
      return null;
    }

    EcatComment ecm = (EcatComment) lst.get(0);
    List<EcatCommentItem> lst1 = getCommentItems(ecm.getComId());
    ecm.setItems(lst1);
    return ecm;
  }

  // this when independent use should be together with paire createTrasncation/closeSession
  @Override
  public List<EcatCommentItem> getCommentItems(Long comId) {
    Session session = getSession();
    Query<EcatCommentItem> sq1 =
        session.createQuery(
            " from EcatCommentItem where comId = :comId  order by itemId", EcatCommentItem.class);
    sq1.setParameter("comId", comId);
    List<EcatCommentItem> lst1 = sq1.list();
    return lst1;
  }

  @Override
  public void deleteComment(Long comId, Long parentId) {
    EcatComment ecm = getEcatComment(comId, parentId);
    if (ecm == null) {
      log.warn("Couldn't delete comment with id {}, it does not exist", comId);
      return;
    }
    deleteComment(ecm);
  }

  @Override
  public void deleteCommentItem(Long itemId) {
    String q1 = "DELETE EcatCommentItem itm where itm.itemId =  :itemId ";
    getSession().createQuery(q1).setParameter("itemId", itemId).executeUpdate();
  }

  @Override
  public void updateCommentItem(EcatCommentItem citm) {
    getSession().update(citm);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public void deleteComments(Long parentId) {
    // createTransaction();
    NativeQuery<EcatComment> sq =
        getSession()
            .createNativeQuery(
                "select * from ecat_comm where parent_id = :parentId  order by com_id",
                EcatComment.class);
    sq.setParameter("parentId", parentId);
    List lst = sq.addEntity(EcatComment.class).list();
    for (int i = 0; i < lst.size(); i++) {
      EcatComment ecm = (EcatComment) lst.get(i);
      deleteComment(ecm);
    }
  }

  protected void deleteComment(EcatComment ecm) {
    Session session = getSessionFactory().getCurrentSession();
    List<EcatCommentItem> its = ecm.getItems();
    if (its != null && its.size() > 0) {
      for (int i = 0; i < its.size(); i++) session.delete(its.get(0));
    }
    session.delete(ecm);
  }
}
