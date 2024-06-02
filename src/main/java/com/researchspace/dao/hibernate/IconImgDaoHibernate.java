package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.IconImgDao;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.record.IconEntity;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository("iconImgDao")
public class IconImgDaoHibernate extends GenericDaoHibernate<ImageBlob, Long>
    implements IconImgDao {

  public IconImgDaoHibernate() {
    super(ImageBlob.class);
  }

  @Override
  public IconEntity getIconEntity(Long id) {
    return (IconEntity) getSession().get(IconEntity.class, id);
  }

  @Override
  public IconEntity saveIconEntity(IconEntity iconEntity, boolean updateRSFormIcon) {
    Session session = getSession();
    IconEntity iconx = (IconEntity) getSession().merge(iconEntity);

    if (updateRSFormIcon) {
      String q1 = "UPDATE RSForm SET iconId=:iconId  WHERE id =:id ";
      NativeQuery<?> query = session.createSQLQuery(q1);
      query.setParameter("iconId", iconx.getId());
      query.setParameter("id", iconEntity.getParentId());
      query.executeUpdate();
    }

    return iconx;
  }

  @Override
  public boolean updateIconRelation(long icon_id, long form_id) {
    boolean fg = true;
    Session session = getSession();
    // System.out.println("update: iconid="+icon_id+" form id="+form_id);
    String q1 = "UPDATE RSForm SET iconId=:iconId  WHERE id =:id ";
    NativeQuery<?> query = session.createSQLQuery(q1);
    query.setParameter("iconId", icon_id);
    query.setParameter("id", form_id);
    query.executeUpdate();
    return fg;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Long> getAllIconIds() {
    return getSession().createQuery("select id from IconEntity").list();
  }
}
