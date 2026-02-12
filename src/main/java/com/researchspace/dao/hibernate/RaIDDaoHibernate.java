package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.RaIDDao;
import com.researchspace.model.raid.UserRaid;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RaIDDaoHibernate extends GenericDaoHibernate<UserRaid, Long> implements RaIDDao {

  public RaIDDaoHibernate() {
    super(UserRaid.class);
  }

  @Override
  public List<UserRaid> getAssociatedRaidByAlias(String serverAlias) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from UserRaid ur where ur.raidServerAlias = :serverAlias", UserRaid.class)
        .setParameter("serverAlias", serverAlias)
        .list();
  }
}
