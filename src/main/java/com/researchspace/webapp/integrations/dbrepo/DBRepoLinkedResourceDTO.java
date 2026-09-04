package com.researchspace.webapp.integrations.dbrepo;

public record DBRepoLinkedResourceDTO(
    String id, String type, String label, String secondaryText, String url) {}
