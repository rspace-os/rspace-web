/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/userDetails")
public interface UserDetailsApi {

  /**
   * Get details of the current user.
   *
   * @param user current user
   */
  @GetMapping("/whoami")
  ApiUser getCurrentUserDetails(User user);

  /**
   * Is current user "operated as" by a sysadmin
   *
   * <p>This endpoint is not documented in swagger, as it is not useful for calling with API key
   * authentication - always returns 'false' in such case.
   *
   * <p>Still, when called from browser's /inventory page, the response should be correct.
   *
   * @param user current user
   * @return true if current user session is "operated as" by a sysadmin
   */
  @GetMapping("/isOperatedAs")
  boolean isOperatedAs(User user);

  /**
   * Get details of users belonging to current user groups.
   *
   * @param user current user
   */
  @GetMapping("/groupMembers")
  List<ApiUser> getGroupMembersForCurrentUser(User user);

  /**
   * Get details of the users matching search query.
   *
   * <p>For users not connected to current user these only public details (i.e. full name +
   * username) are populated.
   *
   * @param query search term
   * @param user current user
   */
  @GetMapping("/search")
  List<ApiUser> searchUserDetails(String query, User user);
}
