package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.ApiSystemUserSearchConfig;
import com.researchspace.api.v1.controller.SysadminApiController.GroupApiPost;
import com.researchspace.api.v1.controller.SysadminApiController.UserApiPost;
import com.researchspace.api.v1.controller.SysadminUserPaginationCriteria;
import com.researchspace.api.v1.model.ApiGroupInfo;
import com.researchspace.api.v1.model.ApiSysadminUserSearchResult;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import java.util.Date;
import javax.servlet.ServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Import external representations into RSpace Documents */
@RequestMapping("/api/v1/sysadmin")
public interface SysadminApi {

  /**
   * Deletes a user. Restricted to:
   *
   * <ul>
   *   <li? Temporary users.
   * <li>Account created &gt; 1 year ago
   * </ul>
   *
   * @param req
   * @param toDeleteId id of user
   * @param sysadmin subject; must have sysadmin role
   */
  @DeleteMapping("/users/temp/{id}")
  @ResponseStatus(code = HttpStatus.NO_CONTENT)
  void deleteTempUserOlderThan1Year(ServletRequest req, Long toDeleteId, User sysadmin);

  /**
   * Deletes any user. Restricted to:
   *
   * <ul>
   *   <li>Account created &gt; 1 year ago
   * </ul>
   *
   * @param maxLastLogin Latest last login for user/group permitted.
   * @param req
   * @param toDeleteId id of user
   * @param sysadmin subject; must have sysadmin role
   */
  @DeleteMapping("/users/{id}")
  @ResponseStatus(code = HttpStatus.NO_CONTENT)
  void deleteAnyUserOlderThan1Year(
      ServletRequest req,
      @RequestParam(name = "maxLastLogin", required = false, defaultValue = "2100-01-01")
          Date maxLastLogin,
      Long toDeleteId,
      User sysadmin);

  /**
   * Gets non-temporary users
   *
   * @param req
   * @param pgCriteria
   * @param srchConfig
   * @param errors
   * @param sysadmin
   * @return
   * @throws BindException
   */
  @GetMapping("/users")
  ApiSysadminUserSearchResult getUsers(
      ServletRequest req,
      SysadminUserPaginationCriteria pgCriteria,
      ApiSystemUserSearchConfig srchConfig,
      BindingResult errors,
      User sysadmin)
      throws BindException;

  /**
   * Creates new user
   *
   * @param req
   * @param userApi
   * @param errors
   * @param sysadmin
   * @return
   * @throws BindException
   */
  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  ApiUser createUser(ServletRequest req, UserApiPost userApi, BindingResult errors, User sysadmin)
      throws BindException;

  /***
   * Enables an existing user
   *
   * @param req
   * @param sysadmin
   * @param userId
   * @return
   * @throws BindException
   */
  @PutMapping("/users/{userId}/enable")
  @ResponseStatus(code = HttpStatus.NO_CONTENT)
  void enableUser(ServletRequest req, User sysadmin, Long userId) throws BindException;

  /***
   * Disables an existing user
   *
   * @param req
   * @param sysadmin
   * @param userId
   * @return
   * @throws BindException
   */
  @PutMapping("/users/{userId}/disable")
  @ResponseStatus(code = HttpStatus.NO_CONTENT)
  void disableUser(ServletRequest req, User sysadmin, Long userId) throws BindException;

  /**
   * Creates a group.
   *
   * @param req
   * @param userApi
   * @param errors
   * @param sysadmin
   * @return
   * @throws BindException
   */
  @PostMapping("/groups")
  @ResponseStatus(HttpStatus.CREATED)
  ApiGroupInfo createGroup(
      ServletRequest req, GroupApiPost userApi, BindingResult errors, User sysadmin)
      throws BindException;
}
