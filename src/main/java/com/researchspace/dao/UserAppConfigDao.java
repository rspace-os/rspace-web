package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementDescriptor;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import java.util.List;
import java.util.Optional;

public interface UserAppConfigDao extends GenericDao<UserAppConfig, Long> {

  /**
   * @param propertyName
   * @param user
   * @return a {@link UserAppConfig} or <code>null</code> if could not be found.
   */
  UserAppConfig findByPropertyNameUser(String propertyName, User user);

  /**
   * Finds the App which has an {@link AppConfigElementDescriptor} with for the given property name
   *
   * @param propName
   * @return An {@link App} or <code>null</code> if not found
   */
  App findAppByPropertyName(String propName);

  /**
   * Retrieves an {@link AppConfigElementSet} based on the ID
   *
   * @param appConfigSetDataId
   * @return
   */
  AppConfigElementSet getAppConfigElementSetById(Long appConfigSetDataId);

  /**
   * Saves or updates an existing {@link AppConfigElementSet}
   *
   * @param saved
   */
  void saveAppConfigElement(AppConfigElementSet saved);

  Optional<App> findAppByAppName(String appName);

  Optional<UserAppConfig> findByAppUser(App app, User user);

  Optional<AppConfigElementSet> findByAppConfigElementSetId(Long appConfigElementSetId);

  List<User> findUsersByPropertyValue(String propertyName, String value);
}
