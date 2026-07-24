package com.axiope.webapp.taglib;

/** Loads the Vite-built legacy JavaScript message catalogue as a blocking script. */
public class I18nMessagesTag extends BundleTag {

  private static final long serialVersionUID = 1L;

  public I18nMessagesTag() {
    setBundle("legacyI18n");
  }

  @Override
  boolean usesModuleScripts() {
    return false;
  }
}
