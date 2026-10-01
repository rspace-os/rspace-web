package com.researchspace.service.resourceaccess;

/** One ordered role in a resource role scheme. Higher numeric ranks have more access. */
public record ResourceRole(String key, int rank) {

  public ResourceRole {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Resource role key must not be blank");
    }
  }
}
