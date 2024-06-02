package com.researchspace.integrations.clustermarket.repository;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.integrations.clustermarket.model.ClustermarketBooking;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("clustermarketBookingRepository")
public class ClustermarketBookingRepositoryImpl
    extends GenericDaoHibernate<ClustermarketBooking, Long>
    implements ClustermarketBookingRepository {
  public ClustermarketBookingRepositoryImpl() {
    super(ClustermarketBooking.class);
  }

  // needs own transaction as runs in threadpool
  @Transactional
  @Override
  public ClustermarketBooking save(ClustermarketBooking object) {
    return super.save(object);
  }

  @Override
  public List<ClustermarketBooking> findByIds(List<Long> ids) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from ClustermarketBooking where id in (:ids)", ClustermarketBooking.class)
        .setParameterList("ids", ids)
        .list();
  }
}
