package com.researchspace.integrations.clustermarket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;
import java.util.List;

public interface ClustermarketService {
  List<JsonNode> getBookingDetails(String[] ids, User user);

  List<JsonNode> getEquipmentDetails(String[] ids, User user);
}
