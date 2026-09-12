package com.researchspace.webapp.integrations.dbrepo;

public record DBRepoColumnDTO(
    String id, String name, String internalName, String type, Integer size) {}
