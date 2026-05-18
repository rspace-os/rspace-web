/**
 * DMP Assistant integration — Portage Network's DMPRoadmap fork.
 *
 * <p>API v2 reference: <a
 * href="https://github.com/portagenetwork/roadmap/wiki/API-v2-Documentation">Portage Network
 * roadmap wiki</a>.
 *
 * <p><b>Known production-host limitation:</b> at the time of writing, bearer-token calls to {@code
 * https://dmp-pgd.ca} are blocked by Cloudflare. End-to-end calls against production therefore fail
 * until Portage allowlists this client or otherwise resolves the WAF rule. All RestTemplate usage
 * in this package is exercised against {@link
 * org.springframework.test.web.client.MockRestServiceServer} in the test suite, which is unaffected
 * by the production WAF.
 *
 * <p><b>Local dev workaround:</b> start the app with {@code -Dmock-dmp-assistant=true} and {@link
 * MockDMPAssistantServer} will boot a WireMock-backed stub on {@code http://localhost:9999}
 * (override port with {@code -Dmock-dmp-assistant.port=…}). The mock impersonates OAuth and every
 * v2 endpoint with canned data, so the Apps-page Connect flow, the plan browser, and all proxy
 * routes appear fully working without ever hitting Cloudflare.
 */
package com.researchspace.webapp.integrations.dmpassistant;
