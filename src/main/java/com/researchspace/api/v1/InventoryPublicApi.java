/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @since 1.91
 */
@RequestMapping("/api/inventory/v1/public")
public interface InventoryPublicApi {

  @GetMapping(value = "/view/{publicLink}")
  ApiInventoryRecordInfo getPublicViewOfInventoryItem(String publicLink, User user);
}
