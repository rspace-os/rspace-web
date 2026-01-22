package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.raid.UserRaid;
import java.util.List;

public interface RaIDDao extends GenericDao<UserRaid, Long> {

  List<UserRaid> getAssociatedRaidByUserAndAlias(User user, String serverAlias);
}
