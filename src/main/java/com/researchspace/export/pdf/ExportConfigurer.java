package com.researchspace.export.pdf;

import com.researchspace.model.User;

public interface ExportConfigurer {

  /**
   * Gets PDF config based on user/system defaults.
   *
   * @param user the exporter
   * @return a {@link ExportToFileConfig}
   */
  ExportToFileConfig getExportConfigWithDefaultPageSizeForUser(User user);
}
