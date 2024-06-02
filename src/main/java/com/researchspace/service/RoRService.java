package com.researchspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;

public interface RoRService {

  JsonNode getSystemRoRDetailsForID(String rorID);

  String getSystemRoRValue();

  String getRorNameForSystemRoRValue();

  void updateSystemRoRValue(String rorID, User updater);
}
