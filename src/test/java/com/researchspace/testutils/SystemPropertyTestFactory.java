package com.researchspace.testutils;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import com.researchspace.model.PropertyDescriptor;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementDescriptor;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.preference.SettingsType;
import com.researchspace.model.system.SystemProperty;

public class SystemPropertyTestFactory {

  /**
   * Creates a boolean system property, default true
   *
   * @return
   */
  public static SystemProperty createASystemProperty() {
    PropertyDescriptor desc = createAPropertyDescriptor();
    return new SystemProperty(desc);
  }

  public static SystemProperty createPermissionEnumSystemProperty() {
    PropertyDescriptor desc = createPermissionEnumPropertyDescriptor();
    return new SystemProperty(desc);
  }

  public static PropertyDescriptor createPermissionEnumPropertyDescriptor() {
    return new PropertyDescriptor(randomAlphabetic(5), SettingsType.ENUM, "DENIED_BY_DEFAULT");
  }

  public static PropertyDescriptor createAPropertyDescriptor() {
    return new PropertyDescriptor(randomAlphabetic(5), SettingsType.BOOLEAN, "true");
  }

  public static PropertyDescriptor createAPropertyDescriptor(
      String name, SettingsType type, String defaultValue) {
    PropertyDescriptor pd = createAPropertyDescriptor();
    pd.setName(name);
    pd.setType(type);
    pd.setDefaultValue(defaultValue);
    return pd;
  }

  /**
   * Gets a UserAppConfig for the specified user with a single boolean config element
   *
   * @param any
   * @return
   */
  public static UserAppConfig createAnyAppWithConfigElements(User any, String appName) {
    App app = createAnyApp();
    app.setName(appName);
    AppConfigElement ace =
        new AppConfigElement(new AppConfigElementDescriptor(createAPropertyDescriptor()), "true");
    AppConfigElementSet aceSet = new AppConfigElementSet();
    aceSet.addConfigElement(ace);
    UserAppConfig appCfg = new UserAppConfig(any, app, true);
    appCfg.addConfigSet(aceSet);
    return appCfg;
  }

  public static App createAnyApp() {
    return new App("app", "app description", true);
  }
}
