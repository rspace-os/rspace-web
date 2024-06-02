/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryExportApiController.ApiInventoryExportSettingsPost;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.model.User;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/inventory/v1/export")
public interface InventoryExportApi {

  /**
   * Prints bytes of CSV file into response stream.
   *
   * @param settings export settings
   * @param user current user
   * @return
   */
  @PostMapping
  ApiJob exportToCsv(
      ApiInventoryExportSettingsPost settings, User user, HttpServletResponse response)
      throws BindException, IOException;
}
