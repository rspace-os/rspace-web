package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.OrganisationDao;
import com.researchspace.model.Organisation;
import java.util.List;
import org.hibernate.Criteria;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

@Repository("organisationDao")
public class OrganisationDaoHibernate extends GenericDaoHibernate<Organisation, Long>
    implements OrganisationDao {

  private static final String[] KEYWORDS =
      new String[] {
        "university",
        "college",
        "state",
        "technology",
        "institute",
        "national",
        "school",
        "medical",
        "sciences",
        "universidade",
        "city",
        "center",
        "polytechnic",
        "technical",
        "central"
      };

  /** Constructor that sets the entity to User.class. */
  public OrganisationDaoHibernate() {
    super(Organisation.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Organisation> getApprovedOrganisations(String term) {
    Criteria criteria = getSession().createCriteria(Organisation.class, "organisation");
    if (keywordStartsWith(term)) {
      criteria.add(Restrictions.ilike("title", term, MatchMode.START));
    } else {
      criteria.add(Restrictions.ilike("title", term, MatchMode.ANYWHERE));
    }
    criteria.add(Restrictions.eq("approved", true));
    criteria.addOrder(Order.asc("title"));
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    criteria.setReadOnly(true).setCacheable(true);
    return criteria.list();
  }

  /**
   * Check if the term is a stop word.
   *
   * @param term
   * @return
   */
  private boolean keywordStartsWith(String term) {
    for (String key : KEYWORDS) {
      if (key.startsWith(term.toLowerCase())) {
        return true;
      }
    }
    return false;
  }
}
