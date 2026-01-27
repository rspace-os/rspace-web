package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.dtos.RaidGroupAssociationDTO;
import com.researchspace.model.raid.UserRaid;
import java.util.Optional;
import java.util.Set;

public interface RaIDServiceManager {

  UserRaid getUserRaid(Long userRaidId);

  Set<RaidGroupAssociationDTO> getAssociatedRaidsByUserAndAlias(User user, String raidServerAlias);

  Optional<RaidGroupAssociationDTO> getAssociatedRaidByUserAliasAndProjectId(
      User user, String raidServerAlias, Long projectGroupId);

  Optional<RaidGroupAssociationDTO> getAssociatedRaidByFolderId(User user, Long folderId);

  void bindRaidToGroupAndSave(User user, RaidGroupAssociationDTO raidToGroupAssociation);

  void unbindRaidFromGroupAndSave(User user, Long projectGroupId);
}
