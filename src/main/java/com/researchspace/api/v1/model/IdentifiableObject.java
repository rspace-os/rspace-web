package com.researchspace.api.v1.model;

public interface IdentifiableObject {

  Long getId();

  LinkableApiObject addSelfLink(String link);
}
