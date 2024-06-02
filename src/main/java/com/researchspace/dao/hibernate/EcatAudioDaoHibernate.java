package com.researchspace.dao.hibernate;

import com.researchspace.dao.EcatAudioDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.EcatAudio;
import org.springframework.stereotype.Repository;

@Repository("ecatAudioDao")
public class EcatAudioDaoHibernate extends GenericDaoHibernate<EcatAudio, Long>
    implements EcatAudioDao {

  public EcatAudioDaoHibernate() {
    super(EcatAudio.class);
  }
}
