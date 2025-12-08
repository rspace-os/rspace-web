package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.StoichiometryMoleculeDao;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
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
  public StructuredDocument getDocContainingMolecule(StoichiometryMolecule molecule) {
    Query<Record> query =
        getSession()
            .createQuery(
                "select r from StoichiometryMolecule m "
                    + " join m.stoichiometry s "
                    + " join s.parentReaction pr "
                    + " join pr.record r "
                    + " where m = :molecule",
                Record.class);
    query.setParameter("molecule", molecule);
    return query.uniqueResult().asStrucDoc();
  }
}
