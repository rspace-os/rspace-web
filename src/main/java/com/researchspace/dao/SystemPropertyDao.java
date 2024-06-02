package com.researchspace.dao;

import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import java.util.List;

/** For CRUD operations on SystemProperty (RSPAC-862) */
public interface SystemPropertyDao extends GenericDao<SystemPropertyValue, Long> {

  /**
   * Retrieves a {@link SystemPropertyValue} with the given systemPropertyName, or <code>null</code>
   * if no such property exists
   *
   * @param systemPropertyName the {@link SystemProperty}'s unique name
   * @return a SystemPropertyValue or <code>null</code> if not found
   */
  SystemPropertyValue findByPropertyName(String systemPropertyName);

  /**
   * Retrieves a {@link SystemPropertyValue} with the given systemPropertyName and community ID, or
   * <code>null</code> if no such property exists
   *
   * @param systemPropertyName the {@link SystemProperty}'s unique name
   * @param communityId ID of the community
   * @return a SystemPropertyValue or <code>null</code> if not found
   */
  SystemPropertyValue findByPropertyNameAndCommunity(String systemPropertyName, Long communityId);

  /**
   * Retrieves a {@link SystemProperty} with the given systemPropertyName, or <code>null</code> if
   * no such property exists
   *
   * @param systemPropertyName the {@link SystemProperty}'s unique name
   * @return a SystemProperty or <code>null</code> if not found
   */
  SystemProperty findPropertyByPropertyName(String systemPropertyName);

  List<SystemProperty> listProperties();

  /**
   * Retrieves every {@link SystemPropertyValue} set by system admin
   *
   * @return
   */
  List<SystemPropertyValue> getAllSysadminProperties();

  /**
   * Retrieves every {@link SystemPropertyValue} about the specified community
   *
   * @param communityId ID of the community
   * @return
   */
  List<SystemPropertyValue> getAllByCommunity(Long communityId);

  /**
   * Deletes {@link SystemPropertyValue} with foreign key to Community
   *
   * @param communityId
   * @return the number of deleted rows
   */
  int deleteSystemPropertyValueByCommunityId(Long communityId);
}
