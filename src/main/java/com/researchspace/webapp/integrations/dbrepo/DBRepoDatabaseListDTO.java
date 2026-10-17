package com.researchspace.webapp.integrations.dbrepo;

import java.util.List;

public record DBRepoDatabaseListDTO(String instanceUrl, List<DBRepoDatabaseDTO> databases) {}
