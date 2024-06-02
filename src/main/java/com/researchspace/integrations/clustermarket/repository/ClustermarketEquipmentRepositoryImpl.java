package com.researchspace.integrations.clustermarket.repository;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.integrations.clustermarket.model.ClustermarketEquipment;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("clustermarketEquipmentRepository")
public class ClustermarketEquipmentRepositoryImpl
    extends GenericDaoHibernate<ClustermarketEquipment, Long>
    implements ClustermarketEquipmentRepository {
  public ClustermarketEquipmentRepositoryImpl() {
    super(ClustermarketEquipment.class);
  }

  // needs own transaction as runs in threadpool
  @Transactional
  @Override
  public ClustermarketEquipment save(ClustermarketEquipment object) {
    return super.save(object);
  }

  @Override
  public List<ClustermarketEquipment> findByIds(List<Long> ids) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from ClustermarketEquipment where id in (:ids)", ClustermarketEquipment.class)
        .setParameterList("ids", ids)
        .list();
  }
}
