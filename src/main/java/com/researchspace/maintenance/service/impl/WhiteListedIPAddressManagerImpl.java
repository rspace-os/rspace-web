package com.researchspace.maintenance.service.impl;

import com.researchspace.dao.WhiteListedIPAddressDao;
import com.researchspace.maintenance.model.WhiteListedSysAdminIPAddress;
import com.researchspace.maintenance.service.WhiteListedIPAddressManager;
import com.researchspace.service.impl.GenericManagerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("whiteListedIPAddressManager")
public class WhiteListedIPAddressManagerImpl
    extends GenericManagerImpl<WhiteListedSysAdminIPAddress, Long>
    implements WhiteListedIPAddressManager {

  @Autowired
  public WhiteListedIPAddressManagerImpl(WhiteListedIPAddressDao dao) {
    setDao(dao);
  }
}
