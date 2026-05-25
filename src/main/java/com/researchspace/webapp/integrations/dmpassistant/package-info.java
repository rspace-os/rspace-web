/**
 * DMP Assistant integration — Portage Network's DMPRoadmap fork.
 *
 * <p>API v2 reference: <a
 * href="https://github.com/portagenetwork/roadmap/wiki/API-v2-Documentation">Portage Network
 * roadmap wiki</a>.
 *
 * <p>Authentication model: users paste a personal access token via the Apps page. The token is
 * stored per-user in {@code UserConnection} (provider name {@code DMPASSISTANT}) and forwarded as a
 * Bearer credential against {@code dmpassistant.base.url} on every v2 call.
 *
 * <p><b>Known production-host limitation:</b> bearer-token traffic to {@code https://dmp-pgd.ca} is
 * subject to Cloudflare WAF rules and Portage allowlisting. End-to-end calls against production
 * therefore fail until Portage allowlists this client's egress IP. Until that happens, point {@code
 * dmpassistant.base.url} at any reachable DMPRoadmap deployment for development.
 */
package com.researchspace.webapp.integrations.dmpassistant;
