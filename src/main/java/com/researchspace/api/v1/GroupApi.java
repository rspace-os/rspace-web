package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiGroupInfo;
import com.researchspace.model.User;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
}
