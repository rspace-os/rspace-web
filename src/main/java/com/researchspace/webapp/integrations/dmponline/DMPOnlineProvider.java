package com.researchspace.webapp.integrations.dmponline;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.rda.model.extras.DMPList;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

/** API Client wrapper for making calls to DMP API */
public interface DMPOnlineProvider {

  DMPList getPlanByUrlId(String id, String accessToken)
      throws MalformedURLException, URISyntaxException;

  JsonNode listPlans(String page, String per_page, String accessToken);
}
