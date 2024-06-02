package com.researchspace.dao.hibernate;

import com.researchspace.dao.EcatChemistryFileDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.EcatChemistryFile;
import org.springframework.stereotype.Repository;

@Repository("ecatChemistryFileDao")
public class EcatChemistryFileDaoHibernate extends GenericDaoHibernate<EcatChemistryFile, Long>
    implements EcatChemistryFileDao {

  public EcatChemistryFileDaoHibernate() {
    super(EcatChemistryFile.class);
  }
}
