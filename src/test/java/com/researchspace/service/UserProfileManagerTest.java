package com.researchspace.service;

import static org.junit.Assert.*;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.model.views.PublicUserList;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserProfileManagerTest extends SpringTransactionalTest {

  private static final String USER2 = "user1a";

  private @Autowired IntegrationsHandler integrationsHandler;
  private @Autowired SystemPropertyManager sysPropMgr;
  private @Autowired UserManager userManager;
  private @Autowired UserProfileManager userProfileManager;

  @Test
  public void generatePublicListingWithOrcidInfo() {
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setGetAllResults();

    ISearchResults<PublicUserList> listing = userProfileManager.getPublicUserListing(pgCrit);
    int initialUserCount = listing.getTotalHits().intValue();

    // add new user
    User user = createAndSaveRandomUser();
    final String uname = user.getUsername();
    // check they are on a listing
    listing = userProfileManager.getPublicUserListing(pgCrit);
    assertEquals(initialUserCount + 1, listing.getTotalHits().intValue());

    PublicUserList retrievedUser = getUser(uname, pgCrit).get();
    assertEquals(null, retrievedUser.getOrcidId());

    // set orcid id for new user
    User sysadmin = logoutAndLoginAsSysAdmin();
    sysPropMgr.save("orcid.available", "ALLOWED", sysadmin);
    String testOrcidId = "testOrcidId";
    Map<String, String> newOptions = new HashMap<>();
    newOptions.put("ORCID_ID", testOrcidId);
    logoutAndLoginAs(user);
    integrationsHandler.saveAppOptions(null, newOptions, "ORCID", true, user);
    // check orcid id is on a listing
    retrievedUser = getUser(uname, pgCrit).get();
    assertEquals(testOrcidId, retrievedUser.getOrcidId());

    // set user's profile as private
    user = userManager.setAsPrivateProfile(true, user);
    // check it's not included in listing
    assertTrue(getUser(uname, pgCrit).isEmpty());

    // backdoor sysadmins shouldn't be listed (RSPAC-2189)
    User backdoorSysadmin = createAndSaveRandomUser();
    assertTrue(getUser(backdoorSysadmin.getUsername(), pgCrit).isPresent());
    backdoorSysadmin.setSignupSource(SignupSource.SSO_BACKDOOR);
    userManager.saveUser(backdoorSysadmin);
    assertFalse(getUser(backdoorSysadmin.getUsername(), pgCrit).isPresent());
  }

  private Optional<PublicUserList> getUser(String uname, PaginationCriteria<User> pgCrit) {
    var listing = userProfileManager.getPublicUserListing(pgCrit);
    return listing.getResults().stream()
        .filter(pul -> pul.getUserInfo().getUsername().equals(uname))
        .findFirst();
  }

  @Test
  public void testGetUserProfileNotNull() {
    User user = userManager.getUserByUsername(USER2);
    UserProfile up = userProfileManager.getUserProfile(user);
    assertNotNull(up);
    assertNotNull(up.getId());
    assertNotNull(userProfileManager.getUserProfile(up.getId()));
  }
}
