import { type ApiReferenceConfigurationWithMultipleSources, ApiReferenceReact } from "@scalar/api-reference-react";
import React from "react";
import i18n from "@/modules/common/i18n";
import "@scalar/api-reference-react/style.css";

/**
 * The docs page is served at <context>/public/apiDocs. We strip that known
 * suffix from the current URL to recover the deployment base (which may include
 * a servlet context path) so the spec URLs and the specs' relative `servers`
 * resolve correctly. This mirrors the trick the previous swagger-ui JSP used.
 */
export function getBaseUrl(href: string = window.location.href): string {
  const index = href.indexOf("/public/apiDocs");
  return index === -1 ? "" : href.substring(0, index);
}

/**
 * Prefill for RSpace's OAuth2 password flow. The client id/secret are the
 * public values the previous swagger-ui page hard-coded via `initOAuth`. The
 * `is_jwt=true` token-request parameter and the request-body credentials
 * location are configured as OpenAPI extensions in the spec YAMLs
 * (`x-scalar-security-body`, `x-scalar-credentials-location`) because Scalar
 * does not expose those as JS config options.
 */
const OAUTH_PASSWORD_FLOW_PREFILL = {
  flows: {
    password: {
      "x-scalar-client-id": "rsInventoryWebClient",
      clientSecret: "rsInventoryPublicSecret",
      selectedScopes: ["all"],
    },
  },
};

/**
 * Disables Scalar's per-document "Ask AI" agent button/drawer. The agent's
 * enabled state is read from each source's `agent.disabled`, so this is applied
 * to every source rather than at the top level.
 */
const DISABLED_AGENT = { disabled: true };

/**
 * Scalar renders a "Powered by Scalar" attribution link in the sidebar footer.
 * There is no configuration option to hide it and the React wrapper does not
 * expose the Vue footer slot, so we hide it with CSS. The footer wrapper has
 * the stable class `darklight-reference`; the attribution is the only anchor in
 * it (the colour-mode toggle is a button), so this leaves the toggle intact.
 * Scalar is MIT-licensed, so removing the rendered attribution is permitted.
 */
const HIDE_POWERED_BY_CSS = ".darklight-reference a { display: none !important; }";

export function createApiDocsConfiguration(baseUrl: string): Partial<ApiReferenceConfigurationWithMultipleSources> {
  return {
    sources: [
      {
        title: i18n.t("apiDocs.sources.eln", { ns: "common" }),
        slug: "rspace-eln",
        url: `${baseUrl}/resources/rspace_api_specs_2_23_0.yaml`,
        default: true,
        agent: DISABLED_AGENT,
      },
      {
        title: i18n.t("apiDocs.sources.inventory", { ns: "common" }),
        slug: "rspace-inventory",
        url: `${baseUrl}/resources/rspace_api_inventory_specs_2_24_0.yaml`,
        agent: DISABLED_AGENT,
      },
    ],
    // Resolves the specs' relative servers (/api/v1, /api/inventory/v1) against
    // the deployment base so "try it out" requests target the right origin.
    baseServerURL: baseUrl,
    authentication: {
      preferredSecurityScheme: "ApiKeySecurity",
      // `securitySchemes` is keyed by the scheme name in each document; schemes
      // absent from a given document are ignored. "OAuth" is the ELN scheme,
      // "OAuthJWT" the Inventory scheme. The extension field used for the
      // client id requires a cast as it is not in the typed scheme shape.
      securitySchemes: {
        OAuth: OAUTH_PASSWORD_FLOW_PREFILL,
        OAuthJWT: OAUTH_PASSWORD_FLOW_PREFILL,
      } as unknown as NonNullable<ApiReferenceConfigurationWithMultipleSources["authentication"]>["securitySchemes"],
    },
    // Expand all operation tags by default.
    defaultOpenAllTags: true,
    // Hide the "Open API Client" button; this page is read-only documentation.
    hideClientButton: true,
    // Hide the "Generate MCP server" button.
    mcp: { disabled: true },
    // Do not pull fonts from fonts.scalar.com; RSpace installs may be air-gapped.
    withDefaultFonts: false,
    // Hide the "Powered by Scalar" attribution link (see HIDE_POWERED_BY_CSS).
    customCss: HIDE_POWERED_BY_CSS,
  };
}

/**
 * Full-page Scalar API reference for the RSpace ELN and Inventory APIs. Renders
 * the public, interactive API documentation that replaces the legacy swagger-ui
 * page at /public/apiDocs.
 */
export default function ApiDocsPage(): React.ReactNode {
  const configuration = React.useMemo(() => createApiDocsConfiguration(getBaseUrl()), []);
  return <ApiReferenceReact configuration={configuration} />;
}
