package com.researchspace.dao.hibernate;

import com.researchspace.dao.EcatVideoDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.EcatVideo;
import org.springframework.stereotype.Repository;

@Repository("ecatVideoDao")
public class EcatVideoDaoHibernate extends GenericDaoHibernate<EcatVideo, Long>
    implements EcatVideoDao {

  public EcatVideoDaoHibernate() {
    super(EcatVideo.class);
  }
}
