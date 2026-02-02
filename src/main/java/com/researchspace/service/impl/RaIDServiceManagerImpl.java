package com.researchspace.service.impl;

import com.researchspace.dao.RaIDDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dtos.RaidGroupAssociationDTO;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.RaIDServiceManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RaIDServiceManagerImpl implements RaIDServiceManager {

  @Autowired private RaIDDao raidDao;
  @Autowired private GroupManager groupManager;
  @Autowired private FolderManager folderManagerImpl;

  @Override
  public UserRaid getUserRaid(Long userRaidId) {
    return raidDao.get(userRaidId);
  }

  @Override
  public Set<RaidGroupAssociationDTO> getAssociatedRaidsByAlias(String raidServerAlias) {
    List<UserRaid> userRaidList = raidDao.getAssociatedRaidByAlias(raidServerAlias);
    return userRaidList.stream().map(RaidGroupAssociationDTO::new).collect(Collectors.toSet());
  }

  @Override
  public Optional<RaidGroupAssociationDTO> getAssociatedRaidByAliasAndProjectId(
      String raidServerAlias, Long projectGroupId) {
    Optional<RaidGroupAssociationDTO> result = Optional.empty();
    Set<RaidGroupAssociationDTO> userRaidAlreadyAssociated =
        this.getAssociatedRaidsByAlias(raidServerAlias);
    if (!userRaidAlreadyAssociated.isEmpty()) {
      result =
          userRaidAlreadyAssociated.stream()
              .filter(raid -> raid.getProjectGroupId().equals(projectGroupId))
              .findAny();
    }
    return result;
  }

  @Override
  public Optional<RaidGroupAssociationDTO> getAssociatedRaidByFolderId(User user, Long folderId) {
    Folder currentFolder = folderManagerImpl.getFolder(folderId, user);
    Group projectGroup = groupManager.getGroupFromAnyLevelOfSharedFolder(user, currentFolder, null);
    return Optional.of(new RaidGroupAssociationDTO(projectGroup.getRaid()));
  }

  @Override
  public void bindRaidToGroupAndSave(User user, RaidGroupAssociationDTO raidToGroupAssociation) {
    Group projectGroup = groupManager.getGroup(raidToGroupAssociation.getProjectGroupId());
    projectGroup.setRaid(
        new UserRaid(
            user,
            projectGroup,
            raidToGroupAssociation.getRaid().getRaidServerAlias(),
            raidToGroupAssociation.getRaid().getRaidTitle(),
            raidToGroupAssociation.getRaid().getRaidIdentifier(),
            raidToGroupAssociation.getRaid().getRaidAgencyUrl()));
    groupManager.saveGroup(projectGroup, false, user);
  }

  @Override
  public void unbindRaidFromGroupAndSave(User user, Long projectGroupId) {
    Group projectGroup = groupManager.getGroup(projectGroupId);
    Long raidIdToRemove = projectGroup.getRaid().getId();
    projectGroup.setRaid(null);
    groupManager.saveGroup(projectGroup, false, user);
    raidDao.remove(raidIdToRemove);
  }
}
