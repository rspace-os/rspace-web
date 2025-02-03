package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Manages App Configurations for Users */
public interface UserAppConfigManager extends GenericManager<UserAppConfig, Long> {

  /**
   * Save or update AppConfigElementSet
   *
   * @param appConfigSetData a Map of propertyName:propertyValue pairs
   * @param appId appConfigSetDataId an optional ID, can be null if is a new, transient data
   * @param trustedOrigin some apps should be only updated from trusted sources
   * @param user
   * @return
   * @throws IllegalArgumentException if:
   *     <ul>
   *       <li>any property is unknown, i.e. it does not have an entry in the PropertyDescriptor
   *           table.
   *       <li>the map size is not the same as the number of configuration elements associated with
   *           the App - i.e not all properties in a set are being saved.
   *     </ul>
   */
  UserAppConfig saveAppConfigElementSet(
      Map<String, String> appConfigSetData,
      Long appConfigSetDataId,
      boolean trustedOrigin,
      User user);

  /**
   * Deletes an {@link AppConfigElementSet}
   *
   * @param appConfigElementSetId
   * @param The current subject
   * @return The deleted item
   */
  AppConfigElementSet deleteAppConfigSet(Long appConfigElementSetId, User subject);

  /**
   * Creates or retrieves a {@link UserAppConfig} for the given user and app name
   *
   * @param appName
   * @param user
   * @return
   * @throws IllegalStateException if appName is unknown
   */
  UserAppConfig getByAppName(String appName, User user);

  /**
   * Retrieve an {@link AppConfigElementSet} for the given appConfigSetDataId
   *
   * @param appConfigSetDataId
   * @return
   * @throws IllegalStateException if appName is unknown
   */
  AppConfigElementSet getAppConfigElementSetById(Long appConfigSetDataId);

  /**
   * Retrieves an {@link AppConfigElementSet} by its id
   *
   * @param appConfigElementSetId
   * @return an Optional<AppConfigElementSet>
   */
  Optional<AppConfigElementSet> findByAppConfigElementSetId(Long appConfigElementSetId);

  /**
   * Retrieves users that have a property set to a specific value. This is used to get users that
   * have some external app user id.
   *
   * @param propertyName
   * @param value
   * @return list of users
   */
  List<User> findByAppConfigValue(String propertyName, String value);
}
