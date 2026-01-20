package com.researchspace.service.impl;

import com.researchspace.dao.RaIDDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dtos.RaidGroupAssociation;
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
  @Autowired private GroupManager grpManager;
  @Autowired private FolderManager folderManagerImpl;

  @Override
  public UserRaid getUserRaid(Long userRaidId) {
    return raidDao.get(userRaidId);
  }

  @Override
  public Set<RaidGroupAssociation> getAssociatedRaidsByUserAndAlias(
      User user, String raidServerAlias) {
    List<UserRaid> userRaidList = raidDao.getAssociatedRaidByUserAndAlias(user, raidServerAlias);
    return userRaidList.stream().map(RaidGroupAssociation::new).collect(Collectors.toSet());
  }

  @Override
  public Optional<RaidGroupAssociation> getAssociatedRaidByUserAliasAndProjectId(
      User user, String raidServerAlias, Long projectGroupId) {
    Optional<RaidGroupAssociation> result = Optional.empty();
    Set<RaidGroupAssociation> userRaidAlreadyAssociated =
        this.getAssociatedRaidsByUserAndAlias(user, raidServerAlias);
    if (!userRaidAlreadyAssociated.isEmpty()) {
      result =
          userRaidAlreadyAssociated.stream()
              .filter(raid -> raid.getProjectGroupId().equals(projectGroupId))
              .findAny();
    }
    return result;
  }

  @Override
  public Optional<RaidGroupAssociation> getAssociatedRaidByFolderId(User user, Long folderId) {
    Folder currentFolder = folderManagerImpl.getFolder(folderId, user);
    Group projectGroup = grpManager.getGroupFromAnyLevelOfSharedFolder(user, currentFolder, null);
    return Optional.of(new RaidGroupAssociation(projectGroup.getRaid()));
  }

  @Override
  public void bindRaidToGroupAndSave(User user, RaidGroupAssociation raidToGroupAssociation) {
    Group projectGroup = grpManager.getGroup(raidToGroupAssociation.getProjectGroupId());
    projectGroup.setRaid(
        new UserRaid(
            user,
            projectGroup,
            raidToGroupAssociation.getRaid().getRaidServerAlias(),
            raidToGroupAssociation.getRaid().getRaidIdentifier()));
    grpManager.saveGroup(projectGroup, false, user);
  }

  @Override
  public void unbindRaidFromGroupAndSave(User user, Long projectGroupId) {
    Group projectGroup = grpManager.getGroup(projectGroupId);
    Long raidIdToRemove = projectGroup.getRaid().getId();
    projectGroup.setRaid(null);
    grpManager.saveGroup(projectGroup, false, user);
    raidDao.remove(raidIdToRemove);
  }
}
