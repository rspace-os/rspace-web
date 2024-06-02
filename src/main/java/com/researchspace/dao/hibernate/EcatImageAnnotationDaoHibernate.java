package com.researchspace.dao.hibernate;

import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.EcatImageAnnotation;
import java.util.List;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository("ecatImageAnnotationDao")
public class EcatImageAnnotationDaoHibernate extends GenericDaoHibernate<EcatImageAnnotation, Long>
    implements EcatImageAnnotationDao {

  public EcatImageAnnotationDaoHibernate() {
    super(EcatImageAnnotation.class);
  }

  public EcatImageAnnotation getFromParentIdAndImageId(Long parentId, Long imageId) {
    Query<EcatImageAnnotation> query =
        getSession()
            .createQuery(
                "from EcatImageAnnotation annot where annot.parentId = :parentId and annot.imageId"
                    + " = :imageId",
                EcatImageAnnotation.class);
    query.setParameter("parentId", parentId);
    query.setParameter("imageId", imageId);
    return getFirstResultOrNull(query);
  }

  /**
   * @param fieldId the db identifier of the record containing this annotation
   * @return A possibly empty but non-<code>null</code> list of EcatImageAnnotation objects for this
   *     field.
   */
  public List<EcatImageAnnotation> getAllImageAnnotationsFromField(Long fieldId) {
    Query<EcatImageAnnotation> sq =
        getSession()
            .createQuery(
                "from EcatImageAnnotation f where f.parentId = :fieldId",
                EcatImageAnnotation.class);
    sq.setParameter("fieldId", fieldId);
    return sq.list();
  }
}
