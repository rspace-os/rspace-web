package com.researchspace.dao.hibernate;

import com.researchspace.dao.ExternalStorageDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.netfiles.ExternalStorageLocation;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository(value = "externalStorageDao")
public class ExternalStorageDaoHibernate extends GenericDaoHibernate<ExternalStorageLocation, Long>
    implements ExternalStorageDao {

  public ExternalStorageDaoHibernate() {
    super(ExternalStorageLocation.class);
  }

  @Override
  public List<ExternalStorageLocation> getAllByOperationUser(Long operationUserId) {
    List<ExternalStorageLocation> externalStorageLocations =
        sessionFactory
            .getCurrentSession()
            .createQuery("from ExternalStorageLocation where operationUser_Id=:operationUserId ")
            .setLong("operationUserId", operationUserId)
            .list();
    return externalStorageLocations;
  }
}
