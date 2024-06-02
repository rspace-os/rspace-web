package com.researchspace.service.impl;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.UserDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.views.PublicUserList;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.UserProfileManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class UserProfileManagerImpl implements UserProfileManager {

  private @Autowired UserExternalIdResolver externalIdResolver;
  private @Autowired UserDao userDao;

  /**
   * Get public listing of all users in the application. Disabled users, and these who asked for
   * their profile to be hidden, are omitted.
   *
   * @param pgCrit not null
   * @return
   */
  @Override
  public ISearchResults<PublicUserList> getPublicUserListing(PaginationCriteria<User> pgCrit) {

    UserSearchCriteria searchCriteria = (UserSearchCriteria) pgCrit.getSearchCriteria();
    if (searchCriteria == null) {
      searchCriteria = new UserSearchCriteria();
    }
    searchCriteria.setOnlyEnabled(true);
    searchCriteria.setOnlyPublicProfiles(true);
    searchCriteria.setWithoutBackdoorSysadmins(true);
    pgCrit.setSearchCriteria(searchCriteria);

    ISearchResults<User> userDBList = userDao.searchUsers(pgCrit);

    List<PublicUserList> listUser = new ArrayList<PublicUserList>();
    for (User u : userDBList.getResults()) {
      PublicUserList pul = new PublicUserList();
      // this must be done on a per user basis because availability depends on
      // community membership
      boolean listOrcidIds =
          externalIdResolver.isIdentifierSchemeAvailable(u, IdentifierScheme.ORCID);
      pul.setUserInfo(u.toPublicInfo());
      pul.setGroups(u.getGroups());
      pul.setShortProfileText(getUserProfile(u).getShortProfileText());
      if (listOrcidIds) {
        Optional<ExternalId> exId =
            externalIdResolver.getExternalIdForUser(u, IdentifierScheme.ORCID);
        pul.setOrcidId(exId.isPresent() ? exId.get().getIdentifier() : null);
      }
      listUser.add(pul);
    }
    return new SearchResultsImpl<PublicUserList>(
        listUser,
        userDBList.getPageNumber(),
        userDBList.getTotalHits(),
        userDBList.getHitsPerPage());
  }

  @Override
  public UserProfile getUserProfile(User user) {
    UserProfile profile = userDao.getUserProfileByUser(user);
    if (profile == null) {
      profile = new UserProfile(user);
      profile = userDao.saveUserProfile(profile);
    }
    return profile;
  }

  @Override
  @CacheEvict(value = "com.researchspace.model.User.fullName", key = "#profile.owner.username")
  public UserProfile saveUserProfile(UserProfile profile) {
    return userDao.saveUserProfile(profile);
  }

  @Override
  public UserProfile getUserProfile(Long profileId) {
    return userDao.getUserProfileById(profileId);
  }
}
