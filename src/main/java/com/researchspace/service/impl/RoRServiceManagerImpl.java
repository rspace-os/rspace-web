package com.researchspace.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.client.RoRClient;
import com.researchspace.model.User;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.RoRService;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import java.util.Iterator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RoRServiceManagerImpl implements RoRService {

  private final RoRClient rorClient;
  private final SystemPropertyManager systemPropertyManager;

  public RoRServiceManagerImpl(RoRClient rorClient, SystemPropertyManager systemPropertyManager) {
    this.rorClient = rorClient;
    this.systemPropertyManager = systemPropertyManager;
  }

  @Override
  @Cacheable(value = "com.researchspace.ror", key = "#rorID")
  public JsonNode getSystemRoRDetailsForID(String rorID) {
    return rorClient.getRoRDetailsForID(rorID);
  }

  @Override
  public String getSystemRoRValue() {
    SystemPropertyValue ror = systemPropertyManager.findByName(SystemPropertyName.RSPACE_ROR);
    if (ror != null) {
      return ror.getValue();
    }
    return null;
  }

  @Override
  public String getRorNameForSystemRoRValue() {
    SystemPropertyValue ror = systemPropertyManager.findByName(SystemPropertyName.RSPACE_ROR);
    String rorID = ror.getValue();
    if (!StringUtils.hasText(rorID)) {
      return "";
    }
    JsonNode rorDetails = getSystemRoRDetailsForID(rorID);
    if (rorDetails.get("name") != null) {
      return rorDetails.get("name").asText();
    } else {
      Iterator<JsonNode> it = rorDetails.get("names").iterator();
      while (it.hasNext()) {
        JsonNode item = it.next();
        if (item.get("types") != null
            && item.get("types").toString().indexOf("ror_display") != -1) {
          return item.get("value").asText();
        }
      }
      return "";
    }
  }

  @Override
  public void updateSystemRoRValue(String rorID, User updater) {
    systemPropertyManager.save(SystemPropertyName.RSPACE_ROR, rorID, updater);
  }
}
