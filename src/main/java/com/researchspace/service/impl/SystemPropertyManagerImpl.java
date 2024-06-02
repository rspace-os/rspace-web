package com.researchspace.service.impl;

import static com.researchspace.CacheNames.INTEGRATION_INFO;
import static com.researchspace.model.preference.HierarchicalPermission.DENIED;
import static com.researchspace.model.preference.HierarchicalPermission.DENIED_BY_DEFAULT;
import static com.researchspace.model.preference.Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP;
import static org.apache.commons.lang.StringUtils.join;

import com.researchspace.core.util.ObjectToStringPropertyTransformer;
import com.researchspace.dao.SystemPropertyDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.SystemPropertyManager;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@CacheConfig(cacheNames = "com.researchspace.model.system.SystemPropertyValue")
@Service("systemPropertyManagerImpl")
public class SystemPropertyManagerImpl extends GenericManagerImpl<SystemPropertyValue, Long>
    implements SystemPropertyManager {

  private SystemPropertyDao syspropdao;

  @Autowired
  public void setSystemPropertyDao(SystemPropertyDao syspropdao) {
    this.syspropdao = syspropdao;
    super.setDao(syspropdao);
  }

  @Autowired private CommunityServiceManager communityMgr;

  public void setCommunityMgr(CommunityServiceManager communityMgr) {
    this.communityMgr = communityMgr;
  }

  @Autowired @Lazy private GroupManager groupMgr;

  public void setGroupMgr(GroupManager groupMgr) {
    this.groupMgr = groupMgr;
  }

  @Override
  @CachePut(key = "#sysPropertyValueId")
  @CacheEvict(allEntries = true, value = INTEGRATION_INFO)
  public SystemPropertyValue save(Long sysPropertyValueId, String newValue, User subject) {
    SystemPropertyValue spv = syspropdao.get(sysPropertyValueId);
    return doSave(spv.getProperty().getName(), newValue, spv, subject);
  }

  @Override
  @CachePut(key = "#propertyUniqueName")
  @CacheEvict(allEntries = true, value = INTEGRATION_INFO)
  public SystemPropertyValue save(String propertyUniqueName, String newValue, User subject) {
    SystemPropertyValue spv = syspropdao.findByPropertyNameAndCommunity(propertyUniqueName, null);
    return doSave(propertyUniqueName, newValue, spv, subject);
  }

  @Override
  @CachePut(key = "#spv.property.name + (#spv.community != null ? #spv.community.id : '')")
  @CacheEvict(allEntries = true, value = INTEGRATION_INFO)
  public SystemPropertyValue save(SystemPropertyValue spv, User subject) {
    handlePropertyChanges(spv, subject);
    return super.save(spv);
  }

  @Override
  public SystemPropertyValue save(SystemPropertyValue spv) {
    throw new UnsupportedOperationException("Must also supply the User subject as an argument");
  }

  private SystemPropertyValue doSave(
      String propertyUniqueName, String newValue, SystemPropertyValue spv, User subject) {
    if (spv == null) {
      log.warn("No value set for {}, creating new system property value.", propertyUniqueName);
      SystemProperty prop = syspropdao.findPropertyByPropertyName(propertyUniqueName);
      if (prop == null) {
        List<String> names = getManagedSystemPropertyNames();
        log.error(
            "Trying to update unknown property [{}], must be one of [{}]",
            propertyUniqueName,
            join(names, ","));
        return null;
      } else {
        spv = new SystemPropertyValue(prop, newValue);
        handlePropertyChanges(spv, subject);
        return syspropdao.save(spv);
      }
    } else {
      spv.setValue(newValue);
      handlePropertyChanges(spv, subject);
      syspropdao.save(spv);
    }
    return spv;
  }

  /**
   * This is called to check if this SystemPropertyValue change has additional effects and apply any
   * necessary changes.
   *
   * @param systemPropertyValue new system property value
   */
  private void handlePropertyChanges(SystemPropertyValue systemPropertyValue, User subject) {
    // In case PI_CAN_EDIT_ALL_WORK_IN_LABGROUP gets disabled system wide, permissions of all PIs to
    // edit every
    // users' document in group have to be revoked.
    if (isPiEditAllPref(systemPropertyValue)
        && prefIsDenied(systemPropertyValue)
        && systemPropertyValue.getCommunity() == null) {
      groupMgr.list().stream()
          .filter(g -> GroupType.LAB_GROUP.equals(g.getGroupType()))
          .forEach(
              group -> {
                groupMgr.authorizeAllPIsToEditAll(group.getId(), subject, false);
              });
    }

    // In case PI_CAN_EDIT_ALL_WORK_IN_LABGROUP gets disabled for this community, permissions of all
    // PIs to edit
    // every users' document in group have to be revoked.
    if (isPiEditAllPref(systemPropertyValue)
        && (prefIsDenied(systemPropertyValue) || prefIsDeniedByDefault(systemPropertyValue))
        && systemPropertyValue.getCommunity() != null) {
      Community community =
          communityMgr.getCommunityWithAdminsAndGroups(systemPropertyValue.getCommunity().getId());
      for (Group group : community.getLabGroups()) {
        groupMgr.authorizeAllPIsToEditAll(group.getId(), subject, false);
      }
    }
  }

  private boolean prefIsDeniedByDefault(SystemPropertyValue systemPropertyValue) {
    return DENIED_BY_DEFAULT.name().equalsIgnoreCase(systemPropertyValue.getValue());
  }

  private boolean prefIsDenied(SystemPropertyValue systemPropertyValue) {
    return DENIED.name().equalsIgnoreCase(systemPropertyValue.getValue());
  }

  private boolean isPiEditAllPref(SystemPropertyValue systemPropertyValue) {
    return PI_CAN_EDIT_ALL_WORK_IN_LABGROUP
        .name()
        .equalsIgnoreCase(systemPropertyValue.getProperty().getName());
  }

  /**
   * Get all system property values as a Map keyed by the property name.
   *
   * @return a Map<String, SystemPropertyValue>
   */
  @Override
  public Map<String, SystemPropertyValue> getAllSysadminPropertiesAsMap() {
    List<SystemPropertyValue> all = getAllSysadminProperties();
    Map<String, SystemPropertyValue> rc = new TreeMap<>();
    for (SystemPropertyValue value : all) {
      rc.put(value.getProperty().getName(), value);
    }
    return rc;
  }

  private List<String> getManagedSystemPropertyNames() {
    List<SystemProperty> allprops = syspropdao.listProperties();
    List<String> names =
        allprops.stream()
            .map(new ObjectToStringPropertyTransformer<>("name"))
            .collect(Collectors.toList());
    return names;
  }

  @Override
  @Cacheable(key = "#name")
  public SystemPropertyValue findByName(String name) {
    return syspropdao.findByPropertyNameAndCommunity(name, null);
  }

  @Override
  @Cacheable(key = "#name + #communityId")
  public SystemPropertyValue findByNameAndCommunity(String name, Long communityId) {
    return syspropdao.findByPropertyNameAndCommunity(name, communityId);
  }

  @Override
  public List<SystemProperty> listSystemPropertyDefinitions() {
    return syspropdao.listProperties();
  }

  @Override
  public List<SystemPropertyValue> getAllSysadminProperties() {
    return syspropdao.getAllSysadminProperties();
  }

  @Override
  public List<SystemPropertyValue> getAllByCommunity(Long communityId) {
    return syspropdao.getAllByCommunity(communityId);
  }
}
