package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.StoichiometryDao;
import com.researchspace.model.stoichiometry.Stoichiometry;
import java.util.Optional;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class StoichiometryDaoHibernate extends GenericDaoHibernate<Stoichiometry, Long>
    implements StoichiometryDao {

  public StoichiometryDaoHibernate() {
    super(Stoichiometry.class);
  }

  @Override
  public Optional<Stoichiometry> findByParentReactionId(Long parentReactionId) {
    Query<Stoichiometry> query =
        getSession()
            .createQuery(
                "from Stoichiometry s where s.parentReaction.id = :parentReactionId",
                Stoichiometry.class);
    query.setParameter("parentReactionId", parentReactionId);
    return Optional.ofNullable(query.uniqueResult());
  }
}
