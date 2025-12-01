package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.StoichiometryInventoryLinkDao;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import org.springframework.stereotype.Repository;

@Repository
public class StoichiometryInventoryLinkDaoHibernate
    extends GenericDaoHibernate<StoichiometryInventoryLink, Long>
    implements StoichiometryInventoryLinkDao {

  public StoichiometryInventoryLinkDaoHibernate() {
    super(StoichiometryInventoryLink.class);
  }
}
