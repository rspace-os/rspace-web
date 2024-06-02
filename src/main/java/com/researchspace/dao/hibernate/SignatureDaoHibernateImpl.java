package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.SignatureDao;
import com.researchspace.model.Signature;
import com.researchspace.model.User;
import com.researchspace.model.Witness;
import com.researchspace.model.record.BaseRecord;
import java.util.List;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository("signatureDao")
public class SignatureDaoHibernateImpl extends GenericDaoHibernate<Signature, Long>
    implements SignatureDao {

  public SignatureDaoHibernateImpl() {
    super(Signature.class);
  }

  public Signature getSignatureByRecordId(Long recordId) {
    Query<Signature> q =
        getSession()
            .createQuery("from Signature where recordSigned.id=:recordId", Signature.class)
            .setParameter("recordId", recordId);
    // should be unique but see RSPAC-1397
    return getFirstResultOrNull(q);
  }

  @Override
  public Witness getWitnessforRecord(Long recordId, User subject) {
    List<Witness> result =
        getSession()
            .createQuery(
                " from Witness  w where w.signature.recordSigned.id=:recordId and"
                    + " w.witness=:subject ",
                Witness.class)
            .setParameter("recordId", recordId)
            .setParameter("subject", subject)
            .list();
    return handleNonUniqueWitnesses(recordId, subject, result);
  }

  Witness handleNonUniqueWitnesses(Long recordId, User subject, List<Witness> result) {
    Witness rc = null;
    if (result.size() == 1) {
      rc = result.get(0);
    } else if (result.size() > 1) {
      log.warn(
          "Non-unique Witness for record {} by user {} -  see RSPAC-1397",
          recordId,
          subject.getUsername());
      // if there is a witness with a non-null completion date, return that - it is the most recent
      final Witness defaultRc = result.get(0);
      rc = result.stream().filter(w -> w.getWitnessesDate() != null).findFirst().orElse(defaultRc);
    }
    return rc;
  }

  @Override
  public Witness saveOrUpdateWitness(Witness witness) {
    return (Witness) getSession().merge(witness);
  }

  @Override
  public Boolean isSigned(Long recordId) {
    return (Boolean)
        getSession()
            .createCriteria(BaseRecord.class)
            .add(Restrictions.idEq(recordId))
            .setProjection(Projections.property("signed"))
            .uniqueResult();
  }

  @Override
  public List<Witness> getOpenWitnessesByWitnessUser(User witness) {
    return getSession()
        .createCriteria(Witness.class)
        .add(Restrictions.eq("witness", witness))
        .add(Restrictions.eq("witnessed", false))
        .list();
  }
}
