package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.WhiteListedIPAddressDao;
import com.researchspace.maintenance.model.WhiteListedSysAdminIPAddress;
import org.springframework.stereotype.Repository;

@Repository("whiteListedIPAddressDao")
public class WhiteListedIPAddressDaoImpl
    extends GenericDaoHibernate<WhiteListedSysAdminIPAddress, Long>
    implements WhiteListedIPAddressDao {

  public WhiteListedIPAddressDaoImpl() {
    super(WhiteListedSysAdminIPAddress.class);
  }
}
