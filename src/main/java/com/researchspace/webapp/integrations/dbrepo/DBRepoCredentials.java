package com.researchspace.webapp.integrations.dbrepo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public record DBRepoCredentials(String username, String password) {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public String serialize() {
    try {
      return OBJECT_MAPPER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize DBRepo credentials.", e);
    }
  }

  public static DBRepoCredentials deserialize(String value) {
    try {
      return OBJECT_MAPPER.readValue(value, DBRepoCredentials.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not deserialize DBRepo credentials.", e);
    }
  }
}
