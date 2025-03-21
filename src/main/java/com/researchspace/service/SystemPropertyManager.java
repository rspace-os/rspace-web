package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.preference.SettingsType;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import java.util.List;
import java.util.Map;

/** Manager for SystemProperty configuration, see RSPAC-861 */
public interface SystemPropertyManager extends GenericManager<SystemPropertyValue, Long> {

  /**
   * @param sysPropertyValueId
   * @param newValue
   * @return the updated SystemPropertyValue
   * @throws IllegalArgumentException if <code>newValue</code> is not compatible with the underlying
   *     {@link SystemProperty} {@link SettingsType}
   * @throws DAOException if <code>sysPropertyValueId</code> is not found in database
   */
  SystemPropertyValue save(Long sysPropertyValueId, String newValue, User subject);

  /**
   * @param name
   * @param newValue
   * @param subject
   * @return the updated SystemPropertyValue, or <code>null</code> if a property with <code>
   *     propertyUniqueName</code> doesn't exist
   * @throws IllegalArgumentException if <code>newValue</code> is not compatible with the underlying
   *     {@link SystemProperty} type
   */
  SystemPropertyValue save(SystemPropertyName name, HierarchicalPermission newValue, User subject);

  SystemPropertyValue save(SystemPropertyName name, String newValue, User subject);

  SystemPropertyValue save(Preference preference, String newValue, User subject);

  @Deprecated // use strongly-typed save method variant
  SystemPropertyValue save(String propertyUniqueName, String newValue, User subject);

  /**
   * @param value the value to update
   * @param subject
   * @return the updated SystemPropertyValue
   * @throws IllegalArgumentException if <code>newValue</code> is not compatible with the underlying
   *     {@link SystemProperty} type
   */
  SystemPropertyValue save(SystemPropertyValue value, User subject);

  /**
   * Gets all SystemPropertyValue keyed by the property name
   *
   * @return A {@link Map}
   */
  Map<String, SystemPropertyValue> getAllSysadminPropertiesAsMap();

  /**
   * Gets a SystemPropertyValue or <code>null</code> if not found
   *
   * @param name
   * @return a {@link SystemPropertyValue}
   */
  SystemPropertyValue findByName(SystemPropertyName name);

  SystemPropertyValue findByName(Preference name);

  @Deprecated // use strongly-typed findByName method variant
  SystemPropertyValue findByName(String name);

  /**
   * Finds a SystemPropertyValue with a specified name and community or <code>null</code> if not
   * found.
   *
   * @param name
   * @param communityId id of a community
   * @return a {@link SystemPropertyValue}
   */
  SystemPropertyValue findByNameAndCommunity(String name, Long communityId);

  /**
   * Gets all system property definitions.
   *
   * @return
   */
  List<SystemProperty> listSystemPropertyDefinitions();

  /**
   * Gets every SystemPropertyValue set by system admin
   *
   * @return
   */
  List<SystemPropertyValue> getAllSysadminProperties();

  /**
   * Gets every SystemPropertyValue about the specified community
   *
   * @return
   */
  List<SystemPropertyValue> getAllByCommunity(Long communityId);
}
