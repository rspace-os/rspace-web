package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.RSMathDao;
import com.researchspace.model.RSMath;
import java.util.List;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class RSMathDaoHibernateImpl extends GenericDaoHibernate<RSMath, Long> implements RSMathDao {

  public RSMathDaoHibernateImpl(Class<RSMath> persistentClass) {
    super(persistentClass);
  }

  public RSMathDaoHibernateImpl() {
    super(RSMath.class);
  }

  /**
   * @param fieldId the db identifier of the record containing this annotation
   * @return A possibly empty but non-<code>null</code> list of RSMath objects for this field.
   */
  public List<RSMath> getAllMathElementsFromField(Long fieldid) {
    Query<RSMath> sq =
        getSession()
            .createQuery(" from RSMath math where math.field.id = :fieldId ", RSMath.class)
            .setParameter("fieldId", fieldid);
    return sq.list();
  }
}
