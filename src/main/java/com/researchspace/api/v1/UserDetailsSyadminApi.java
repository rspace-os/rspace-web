/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.model.User;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** returns user details available to sysadmins - only sysadmins will be allowed to use this API */
@RequestMapping("/api/v1/syadmin/userDetails")
public interface UserDetailsSyadminApi {

  @GetMapping("/apiKeyInfo/all")
  @ResponseBody
  /**
   * returns a map of all users usernames to api keys. Request user must be a sysadmin or request
   * will not be authorised
   */
  Map<String, String> getAllApiKeyInfo(@RequestAttribute(name = "user") User user);
}
