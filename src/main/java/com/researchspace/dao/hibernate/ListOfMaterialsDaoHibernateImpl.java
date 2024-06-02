package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.ListOfMaterialsDao;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.elninventory.ListOfMaterials;
import java.util.List;
import org.hibernate.type.LongType;
import org.springframework.stereotype.Repository;

@Repository
public class ListOfMaterialsDaoHibernateImpl extends GenericDaoHibernate<ListOfMaterials, Long>
    implements ListOfMaterialsDao {

  public ListOfMaterialsDaoHibernateImpl(Class<ListOfMaterials> persistentClass) {
    super(persistentClass);
  }

  public ListOfMaterialsDaoHibernateImpl() {
    super(ListOfMaterials.class);
  }

  @Override
  public List<ListOfMaterials> findLomsByElnFieldIds(Long... elnFieldIds) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from ListOfMaterials where elnField_id in (:elnFieldIds)", ListOfMaterials.class)
        .setParameterList("elnFieldIds", elnFieldIds)
        .list();
  }

  @Override
  public List<ListOfMaterials> findLomsByInvRecGlobalId(GlobalIdentifier invRecGlobalId) {

    if (GlobalIdPrefix.BE.equals(invRecGlobalId.getPrefix())
        || GlobalIdPrefix.IT.equals(invRecGlobalId.getPrefix())) {
      return List.of(); // return early, workbench and templates shouldn't ever be on LoM
    }

    String invRecTypeColumnName;
    if (GlobalIdPrefix.SA.equals(invRecGlobalId.getPrefix())) {
      invRecTypeColumnName = "sample_id";
    } else if (GlobalIdPrefix.SS.equals(invRecGlobalId.getPrefix())) {
      invRecTypeColumnName = "subSample_id";
    } else if (GlobalIdPrefix.IC.equals(invRecGlobalId.getPrefix())) {
      invRecTypeColumnName = "container_id";
    } else {
      throw new IllegalArgumentException("unsupported lom search global id type");
    }

    List<Long> lomIds =
        sessionFactory
            .getCurrentSession()
            .createSQLQuery(
                "select distinct parentLom_id from MaterialUsage "
                    + " where "
                    + invRecTypeColumnName
                    + " = :invRecId")
            .setParameter("invRecId", invRecGlobalId.getDbId())
            .addScalar("parentLom_id", LongType.INSTANCE)
            .list();

    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from ListOfMaterials lom "
                + "left join fetch lom.elnField elnField "
                + "left join fetch elnField.structuredDocument "
                + "where lom.id in :lomIds",
            ListOfMaterials.class)
        .setParameterList("lomIds", lomIds)
        .list();
  }
}
