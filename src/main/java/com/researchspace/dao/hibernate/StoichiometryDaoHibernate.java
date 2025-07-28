package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.StoichiometryDao;
import com.researchspace.model.Stoichiometry;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

/** Hibernate implementation of the StoichiometryDao interface. */
@Repository("stoichiometryDao")
public class StoichiometryDaoHibernate extends GenericDaoHibernate<Stoichiometry, Long>
    implements StoichiometryDao {

  /** Constructor. */
  public StoichiometryDaoHibernate() {
    super(Stoichiometry.class);
  }

  /** {@inheritDoc} */
  @Override
  public Stoichiometry findByParentReactionId(Long parentReactionId) {
    Query<Stoichiometry> query =
        getSession()
            .createQuery(
                "from Stoichiometry s where s.parentReaction.id = :parentReactionId",
                Stoichiometry.class);
    query.setParameter("parentReactionId", parentReactionId);
    return query.uniqueResult();
  }
}
