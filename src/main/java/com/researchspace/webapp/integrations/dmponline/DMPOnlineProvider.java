package com.researchspace.webapp.integrations.dmponline;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.rda.model.extras.DMPList;

/** API Client wrapper for making calls to DMP API */
public interface DMPOnlineProvider {

  DMPList getPlanByUrlId(String id, String accessToken);

  JsonNode listPlans(int page, int per_page, String accessToken);
}
