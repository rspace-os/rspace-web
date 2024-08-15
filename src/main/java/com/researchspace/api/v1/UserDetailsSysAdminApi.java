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

/**
 * returns user details available to sysadmins - only sysadmins will be allowed to use this API, and
 * only if sysadmin.apikey.access deployment property is set to true
 */
@RequestMapping("/api/v1/sysadmin/userDetails")
public interface UserDetailsSysAdminApi {

  /**
   * returns a map of all users usernames to api keys. Request user must be a sysadmin, and
   * sysadmin.apikey.access deployment property must be set to 'true', or request will be rejected
   */
  @GetMapping("/apiKeyInfo/all")
  @ResponseBody
  Map<String, String> getAllApiKeyInfo(@RequestAttribute(name = "user") User user);
}
