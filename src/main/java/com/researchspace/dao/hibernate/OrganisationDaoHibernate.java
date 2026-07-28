package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.OrganisationDao;
import com.researchspace.model.Organisation;
import java.util.List;
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
    String likeTerm = term.toLowerCase();
    if (keywordStartsWith(term)) {
      likeTerm = likeTerm + "%";
    } else {
      likeTerm = "%" + likeTerm + "%";
    }
    return getSession()
        .createQuery(
            "select distinct o from Organisation o "
                + "where o.approved = true and lower(o.title) like :term "
                + "order by o.title asc",
            Organisation.class)
        .setParameter("term", likeTerm)
        .setReadOnly(true)
        .setCacheable(true)
        .list();
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
