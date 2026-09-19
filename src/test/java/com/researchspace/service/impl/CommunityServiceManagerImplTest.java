package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.testutils.TestFactory.createAnyUserWithRole;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Community;
import com.researchspace.model.User;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CommunityServiceManagerImplTest {

  @Mock UserDao userDao;
  @Mock CommunityDao communityDao;
  @InjectMocks CommunityServiceManagerImpl impl;

  User communityAdmin1, communityAdmin2;

  @BeforeEach
  public void setUp() throws Exception {
    communityAdmin1 = createAnyUserWithRole("any", Constants.ADMIN_ROLE);
    communityAdmin1.setId(1L);
    communityAdmin2 = createAnyUserWithRole("any2", Constants.ADMIN_ROLE);
    communityAdmin2.setId(2L);
  }

  @Test
  public void isUserUniqueAdminInAnyCommunityIsFalseNotInCommunity() {
    when(communityDao.hasCommunity(communityAdmin1)).thenReturn(false);
    assertThat(impl.isUserUniqueAdminInAnyCommunity(communityAdmin1), is(false));
  }

  @Test
  public void isUserUniqueAdminInAnyCommunityTrue() {
    Community community = createCommunityWithCommunityAdmin1();
    when(communityDao.hasCommunity(communityAdmin1)).thenReturn(true);
    when(communityDao.listCommunitiesForAdmin(communityAdmin1.getId()))
        .thenReturn(toList(community));

    assertThat(impl.isUserUniqueAdminInAnyCommunity(communityAdmin1), is(true));
  }

  @Test
  public void isUserUniqueAdminInAnyCommunityFalseIfOtherAdminExists() {
    // community has 2 admins
    Community community = createCommunityWithCommunityAdmin1();
    community.addAdmin(communityAdmin2);
    when(communityDao.hasCommunity(communityAdmin1)).thenReturn(true);
    when(communityDao.listCommunitiesForAdmin(communityAdmin1.getId()))
        .thenReturn(toList(community));
    assertThat(impl.isUserUniqueAdminInAnyCommunity(communityAdmin1), is(false));
  }

  @Test
  public void isUserUniqueAdminInAnyCommunityTrueIfUniqueInAnyCommunity() {
    // community has 2 admins
    Community community1 = createCommunityWithCommunityAdmin1();
    community1.addAdmin(communityAdmin2);
    // this has only 1
    Community community2 = createCommunityWithCommunityAdmin1();

    when(communityDao.hasCommunity(communityAdmin1)).thenReturn(true);
    when(communityDao.listCommunitiesForAdmin(communityAdmin1.getId()))
        .thenReturn(toList(community1, community2));
    assertThat(impl.isUserUniqueAdminInAnyCommunity(communityAdmin1), is(true));
  }

  private Community createCommunityWithCommunityAdmin1() {
    Community community = TestFactory.createACommunity();
    community.addAdmin(communityAdmin1);
    return community;
  }
}
