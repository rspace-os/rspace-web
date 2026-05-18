package com.researchspace.webapp.integrations.dmpassistant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Thin client over the DMP Assistant (Portage Network roadmap) HTTP API v2. Every method takes the
 * caller's OAuth access token; the implementation forwards it as a Bearer header.
 */
public interface DMPAssistantProvider {

  /** GET /api/v2/heartbeat — unauthenticated liveness probe. */
  JsonNode heartbeat();

  /** GET /api/v2/me — returns the authenticated user's profile. */
  JsonNode me(String accessToken);

  /** GET /api/v2/plans — paginated list of plans visible to the token holder. */
  JsonNode listPlans(String page, String perPage, Boolean complete, String accessToken);

  /** GET /api/v2/plans/{id} — single plan, optionally with questions/answers. */
  JsonNode getPlanById(String id, Boolean complete, String accessToken);

  /**
   * POST /api/v2/plans — creates a plan from an RDA DMP Common Standard body. Needs scope=write.
   */
  JsonNode createPlan(JsonNode plan, String accessToken);

  /** PUT /api/v2/plans/{id} — submits answers for a plan. Needs scope=write. */
  JsonNode editPlanAnswers(String id, JsonNode answers, String accessToken);

  /** GET /api/v2/templates — paginated list of templates. */
  JsonNode listTemplates(String accessToken);

  /** GET /api/v2/templates/{id} — single template. */
  JsonNode getTemplateById(String id, String accessToken);
}
