package com.researchspace.integrations.clustermarket.repository;

import com.researchspace.dao.GenericDao;
import com.researchspace.integrations.clustermarket.model.ClustermarketBooking;
import java.util.List;

public interface ClustermarketBookingRepository extends GenericDao<ClustermarketBooking, Long> {

  List<ClustermarketBooking> findByIds(List<Long> ids);
}
