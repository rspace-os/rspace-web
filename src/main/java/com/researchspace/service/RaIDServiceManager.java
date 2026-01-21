package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.dtos.RaidGroupAssociation;
import com.researchspace.model.raid.UserRaid;
import java.util.Optional;
import java.util.Set;

public interface RaIDServiceManager {

  UserRaid getUserRaid(Long userRaidId);

  Set<RaidGroupAssociation> getAssociatedRaidsByUserAndAlias(User user, String raidServerAlias);

  Optional<RaidGroupAssociation> getAssociatedRaidByUserAliasAndProjectId(
      User user, String raidServerAlias, Long projectGroupId);

  Optional<RaidGroupAssociation> getAssociatedRaidByFolderId(User user, Long folderId);

  void bindRaidToGroupAndSave(User user, RaidGroupAssociation raidToGroupAssociation);

  void unbindRaidFromGroupAndSave(User user, Long projectGroupId);
}
