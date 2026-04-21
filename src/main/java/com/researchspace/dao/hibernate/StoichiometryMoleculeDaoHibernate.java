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
                "select stoich.record from StoichiometryMolecule mol join mol.stoichiometry stoich "
                    + " where mol.id = :moleculeId",
                Record.class);
    query.setParameter("moleculeId", molecule.getId());
    return query.uniqueResult().asStrucDoc();
  }
}
