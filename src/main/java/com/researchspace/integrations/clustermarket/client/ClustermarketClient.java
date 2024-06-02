package com.researchspace.integrations.clustermarket.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;

public interface ClustermarketClient {
  JsonNode getBookings(User user, String accessToken);

  JsonNode getBookingDetails(String id, User user);

  JsonNode getEquipmentDetails(String id, User user);
}
