package com.researchspace.dao.hibernate;

import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.EcatImage;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository("ecatImageDao")
public class EcatImageDaoHibernate extends GenericDaoHibernate<EcatImage, Long>
    implements EcatImageDao {

  public EcatImageDaoHibernate() {
    super(EcatImage.class);
  }

  @Override
  public EcatImage getWithOriginalImage(Long id) {
    List<EcatImage> results =
        getSession()
            .createQuery(
                "select i from EcatImage i left join fetch i.originalImage where i.id = :id",
                EcatImage.class)
            .setParameter("id", id)
            .list();
    return results.isEmpty() ? null : results.get(0);
  }
}
