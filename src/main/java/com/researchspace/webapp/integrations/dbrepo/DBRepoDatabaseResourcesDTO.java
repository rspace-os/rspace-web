package com.researchspace.webapp.integrations.dbrepo;

import java.util.List;

public record DBRepoDatabaseResourcesDTO(
    String databaseId,
    List<DBRepoLinkedResourceDTO> tables,
    List<DBRepoLinkedResourceDTO> views,
    List<DBRepoLinkedResourceDTO> subsets,
    List<String> failedTypes) {}
