package com.researchspace.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.model.views.PublicUserList;

public interface UserProfileManager {

  /**
   * Gets profile information for the user, creating a new default, empty profile if this user does
   * not yet have a profile.
   */
  UserProfile getUserProfile(User user);

  /** Gets profile information for an existing profileID. */
  UserProfile getUserProfile(Long profileId);

  /**
   * Saves or updates a UserProfile
   *
   * @param profile
   * @return the saved profile
   */
  UserProfile saveUserProfile(UserProfile profile);

  ISearchResults<PublicUserList> getPublicUserListing(PaginationCriteria<User> pgCrit);
}
