package com.researchspace.integrations.jove.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple Pojo to represent a jove access token as jove api doesn't currently fit into our existing
 * OAuth flows
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoveToken {

  private String token;
  private Long expires;
}
