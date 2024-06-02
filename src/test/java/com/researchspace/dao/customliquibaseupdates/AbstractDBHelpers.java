package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.model.field.TextField;
import com.researchspace.model.record.Snippet;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * As a result of updating to Hibernate 5.2 RSPAC-1438, it seems there may be 2 session factories,
 * 1created by test execution,and1 in liquibase. So opening a transaction in a test must call code
 * in the test class and not in the Liquibaseupdate class. <br>
 * This results in some duplication of code, unless we refactor these methods to take a
 * sessionfactory as an argument.
 */
public abstract class AbstractDBHelpers extends RealTransactionSpringTestBase {

  protected List<TextField> getAllTextFieldsWithLinks(String linkMatcher) {
    Query<TextField> query =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from TextField where rtfData  like '%" + linkMatcher + "%'", TextField.class);
    return query.list();
  }

  protected List<Snippet> getAllSnippets() {
    Session session = sessionFactory.getCurrentSession();
    CriteriaBuilder criteria = session.getCriteriaBuilder();
    CriteriaQuery<Snippet> query = criteria.createQuery(Snippet.class);
    Root<Snippet> root = query.from(Snippet.class);
    query.select(root);
    return session.createQuery(query).getResultList();
  }
}
