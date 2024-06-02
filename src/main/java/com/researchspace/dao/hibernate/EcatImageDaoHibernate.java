package com.researchspace.dao.hibernate;

import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.EcatImage;
import org.springframework.stereotype.Repository;

@Repository("ecatImageDao")
public class EcatImageDaoHibernate extends GenericDaoHibernate<EcatImage, Long>
    implements EcatImageDao {

  public EcatImageDaoHibernate() {
    super(EcatImage.class);
  }
}
