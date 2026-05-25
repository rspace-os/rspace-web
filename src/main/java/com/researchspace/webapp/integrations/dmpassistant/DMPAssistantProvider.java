package com.researchspace.webapp.integrations.dmpassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;

/**
 * Thin client over the DMP Assistant (Portage Network roadmap) HTTP API v2. Each method looks up
 * the caller's personal access token from {@code UserConnection} and forwards it as a Bearer
 * header.
 */
public interface DMPAssistantProvider {

  /** GET /api/v2/me — returns the authenticated user's profile. */
  JsonNode me(User user);

  /** GET /api/v2/plans — paginated list of plans visible to the token holder. */
  JsonNode listPlans(String page, String perPage, Boolean complete, User user);

  /** GET /api/v2/plans/{id} — single plan, optionally with questions/answers. */
  JsonNode getPlanById(String id, Boolean complete, User user);

  /**
   * POST /api/v2/plans — creates a plan from an RDA DMP Common Standard body. Needs scope=write.
   */
  JsonNode createPlan(JsonNode plan, User user);

  /** PUT /api/v2/plans/{id} — submits answers for a plan. Needs scope=write. */
  JsonNode editPlanAnswers(String id, JsonNode answers, User user);

  /** GET /api/v2/templates — paginated list of templates. */
  JsonNode listTemplates(User user);

  /** GET /api/v2/templates/{id} — single template. */
  JsonNode getTemplateById(String id, User user);
}
