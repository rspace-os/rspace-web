package com.researchspace.export.pdf;

import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.preference.ExportPageSize;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.UserManager;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class ExportConfigurerImpl implements ExportConfigurer {

  private Logger log = LoggerFactory.getLogger(ExportConfigurerImpl.class);

  @Autowired private UserManager userManager;

  @Value("${pdf.defaultPageSize}")
  private String systemDefault;

  @Override
  public ExportToFileConfig getExportConfigWithDefaultPageSizeForUser(User user) {
    ExportToFileConfig cfg = new ExportToFileConfig();
    ExportPageSize defaultPageSize = getDefault(user);
    cfg.setPageSize(defaultPageSize.name());
    return cfg;
  }

  ExportPageSize getSystemDefault() {
    if (!StringUtils.isEmpty(systemDefault)) {
      try {
        return ExportPageSize.valueOf(systemDefault);
      } catch (IllegalArgumentException e) {
        log.error(
            "Unknown system pdf.defaultPageSize {}, must be one of {}",
            systemDefault,
            StringUtils.join(ExportPageSize.values(), ","));
        return ExportPageSize.UNKNOWN;
      }
    } else {
      log.warn("No system default set, returning {}", ExportPageSize.UNKNOWN);
      return ExportPageSize.UNKNOWN;
    }
  }

  void setSystemDefault(String systemDefault) {
    this.systemDefault = systemDefault;
  }

  ExportPageSize getUserDefault(User user) {
    UserPreference up = userManager.getPreferenceForUser(user, Preference.UI_PDF_PAGE_SIZE);
    String errorMsg = Preference.UI_PDF_PAGE_SIZE.getInvalidErrorMessageForValue(up.getValue());
    if (!StringUtils.isEmpty(errorMsg)) {
      log.error(errorMsg);
      return ExportPageSize.UNKNOWN;
    } else {
      return ExportPageSize.valueOf(up.getValue());
    }
  }

  ExportPageSize getDefault(User user) {
    // try user pref
    ExportPageSize rc = getUserDefault(user);
    if (ExportPageSize.UNKNOWN.equals(rc)) {
      rc = getSystemDefault();
    }
    return rc;
  }

  /* --- for testing ---- */
  void setUserManager(UserManager userManager) {
    this.userManager = userManager;
  }
}
