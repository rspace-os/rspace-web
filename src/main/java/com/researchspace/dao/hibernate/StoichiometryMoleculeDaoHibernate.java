package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.StoichiometryMoleculeDao;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class StoichiometryMoleculeDaoHibernate
    extends GenericDaoHibernate<StoichiometryMolecule, Long> implements StoichiometryMoleculeDao {
  public StoichiometryMoleculeDaoHibernate() {
    super(StoichiometryMolecule.class);
  }

  @Override
  public BaseRecord getDocContainingMolecule(StoichiometryMolecule molecule) {
    if (molecule == null) {
      return null;
    }

    Query<BaseRecord> query =
        getSession()
            .createQuery(
                "select r from StoichiometryMolecule m "
                    + " join m.stoichiometry s "
                    + " join s.parentReaction pr "
                    + " join pr.record r "
                    + " where m = :molecule",
                BaseRecord.class);
    query.setParameter("molecule", molecule);
    return query.uniqueResult();
  }
}
