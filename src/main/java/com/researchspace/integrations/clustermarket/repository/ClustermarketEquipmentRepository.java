package com.researchspace.integrations.clustermarket.repository;

import com.researchspace.dao.GenericDao;
import com.researchspace.integrations.clustermarket.model.ClustermarketEquipment;
import java.util.List;

public interface ClustermarketEquipmentRepository extends GenericDao<ClustermarketEquipment, Long> {
  List<ClustermarketEquipment> findByIds(List<Long> ids);
}
