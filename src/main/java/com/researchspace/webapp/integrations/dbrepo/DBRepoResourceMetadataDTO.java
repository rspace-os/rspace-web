package com.researchspace.webapp.integrations.dbrepo;

import java.util.List;

public record DBRepoResourceMetadataDTO(
    String id, String type, String name, String query, List<DBRepoColumnDTO> columns) {}
