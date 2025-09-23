package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiGroupInfo;
import com.researchspace.model.User;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Import external representations into RSpace Documents */
@RequestMapping("/api/v1/groups")
public interface GroupApi {

  /**
   * Lists groups to which the subject belongs
   *
   * @param user the authenticated subject
   * @return
   */
  @GetMapping
  @ResponseStatus(code = HttpStatus.OK)
  List<ApiGroupInfo> listCurrentUserGroups(User user);

  /**
   * Get RSpace groups matching search query.
   *
   * @param query search term
   * @param user current user
   */
  @GetMapping("/search")
  List<ApiGroupInfo> searchGroups(String query, User user);

  /**
   * Get details of a specific group by ID
   *
   * @param id the group ID
   * @param user current user
   * @return group details
   */
  @GetMapping("/{id}")
  ApiGroupInfo getUserGroupById(@PathVariable("id") Long id, User user);
}
