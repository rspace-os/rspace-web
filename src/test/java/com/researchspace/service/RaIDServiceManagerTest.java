package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.dao.RaIDDao;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.model.dtos.RaidGroupAssociation;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.service.impl.RaIDServiceManagerImpl;
import com.researchspace.testutils.TestFactory;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RaIDServiceManagerTest {

  private static final String SERVER_ALIAS = "raidServerAlias";
  private static final String RAID_IDENTIFIER = "https://raid.org/10.12345/FJK987";
  private static final String RAID_TITLE = "Raid Title 1";
  public static final long USER_RAID_ID = 1L;
  public static final long PROJECT_GROUP_ID = 2L;

  @InjectMocks private RaIDServiceManager raIDServiceManager = new RaIDServiceManagerImpl();

  @Mock private RaIDDao raidDao;

  @Mock private GroupManager groupManager;

  private User piUser;
  private Group projectGroup;
  private UserRaid userRaid;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    piUser = TestFactory.createAnyUserWithRole("pi", Constants.PI_ROLE);
    projectGroup = TestFactory.createAnyGroup(piUser);
    projectGroup.setId(PROJECT_GROUP_ID);
    projectGroup.setGroupType(GroupType.PROJECT_GROUP);
    userRaid = new UserRaid(piUser, projectGroup, SERVER_ALIAS, RAID_TITLE, RAID_IDENTIFIER);
    userRaid.setId(USER_RAID_ID);

    when(raidDao.getAssociatedRaidByUserAndAlias(piUser, SERVER_ALIAS))
        .thenReturn(List.of(userRaid));
    when(groupManager.getGroup(projectGroup.getId())).thenReturn(projectGroup);
  }

  @Test
  public void testGetAssociatedRaidsByUserAndAlias() {
    // WHEN
    Set<RaidGroupAssociation> actualResult =
        raIDServiceManager.getAssociatedRaidsByUserAndAlias(piUser, SERVER_ALIAS);

    // THEN
    assertNotNull(actualResult);
    assertEquals(1, actualResult.size());
    RaidGroupAssociation actualReferenceDTO = actualResult.iterator().next();
    assertEquals(USER_RAID_ID, actualReferenceDTO.getRaid().getId());
    assertEquals(SERVER_ALIAS, actualReferenceDTO.getRaid().getRaidServerAlias());
    assertEquals(RAID_IDENTIFIER, actualReferenceDTO.getRaid().getRaidIdentifier());
  }

  @Test
  public void testBindRaidToGroupAndSave() {
    // WHEN
    raIDServiceManager.bindRaidToGroupAndSave(
        piUser,
        new RaidGroupAssociation(
            projectGroup.getId(),
            projectGroup.getDisplayName(),
            new RaIDReferenceDTO(SERVER_ALIAS, RAID_TITLE, RAID_IDENTIFIER)));

    // THEN
    verify(groupManager).getGroup(projectGroup.getId());
    verify(groupManager).saveGroup(projectGroup, false, piUser);
  }

  @Test
  public void testUnbindRaidFromGroupAndSave() {
    // GIVEN
    projectGroup.setRaid(userRaid);

    // WHEN
    raIDServiceManager.unbindRaidFromGroupAndSave(piUser, projectGroup.getId());

    // THEN
    verify(groupManager).getGroup(projectGroup.getId());
    verify(groupManager).saveGroup(projectGroup, false, piUser);
    verify(raidDao).remove(userRaid.getId());
  }
}
