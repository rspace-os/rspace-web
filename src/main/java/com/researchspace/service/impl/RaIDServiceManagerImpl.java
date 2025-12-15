package com.researchspace.service.impl;

import com.researchspace.dao.RaIDDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dtos.RaidGroupAssociation;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.service.GroupManager;
import com.researchspace.service.RaIDServiceManager;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RaIDServiceManagerImpl implements RaIDServiceManager {

  @Autowired private RaIDDao raidDao;
  @Autowired private GroupManager groupManager;

  @Override
  public UserRaid getUserRaid(Long userRaidId) {
    return raidDao.get(userRaidId);
  }

  @Override
  public Set<RaidGroupAssociation> getAssociatedRaidsByUserAndAlias(User user, String serverAlias) {
    List<UserRaid> userRaidList = raidDao.getAssociatedRaidByUserAndAlias(user, serverAlias);
    return userRaidList.stream()
        .map(
            r ->
                new RaidGroupAssociation(
                    r.getGroupAssociated().getId(),
                    new RaIDReferenceDTO(r.getId(), r.getRaidServerAlias(), r.getRaidIdentifier())))
        .collect(Collectors.toSet());
  }

  @Override
  public void bindRaidToGroupAndSave(User user, RaidGroupAssociation raidToGroupAssociation) {
    Group projectGroup = groupManager.getGroup(raidToGroupAssociation.getProjectGroupId());
    projectGroup.setRaid(
        new UserRaid(
            user,
            projectGroup,
            raidToGroupAssociation.getRaid().getRaidServerAlias(),
            raidToGroupAssociation.getRaid().getRaidIdentifier()));
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
