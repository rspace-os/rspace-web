import { screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { render } from "@/__tests__/customQueries";

/*
 * The real Scalar component boots an embedded Vue app that depends on browser
 * APIs unavailable under jsdom, so we replace it with a stub that surfaces the
 * configuration it was handed for assertions.
 */
vi.mock("@scalar/api-reference-react", () => ({
  ApiReferenceReact: ({ configuration }: { configuration: unknown }) => (
    <div data-testid="mock-scalar" data-configuration={JSON.stringify(configuration)} />
  ),
}));
vi.mock("@scalar/api-reference-react/style.css", () => ({}));

import ApiDocsPage, { createApiDocsConfiguration, getBaseUrl } from "./ApiDocsPage";

describe("getBaseUrl", () => {
  test("strips the /public/apiDocs suffix for a root deployment", () => {
    expect(getBaseUrl("https://example.com/public/apiDocs")).toBe("https://example.com");
  });

  test("preserves a servlet context path", () => {
    expect(getBaseUrl("https://example.com/rspace/public/apiDocs")).toBe("https://example.com/rspace");
  });

  test("returns an empty base when the suffix is absent", () => {
    expect(getBaseUrl("https://example.com/somewhere/else")).toBe("");
  });
});

describe("createApiDocsConfiguration", () => {
  const config = createApiDocsConfiguration("https://example.com");

  test("declares both API specs with the expected URLs", () => {
    expect(config.sources).toHaveLength(2);
    const [eln, inventory] = config.sources ?? [];
    expect(eln).toMatchObject({
      title: "common:apiDocs.sources.eln",
      slug: "rspace-eln",
      url: "https://example.com/resources/rspace_api_specs_2_23_0.yaml",
      default: true,
    });
    expect(inventory).toMatchObject({
      title: "common:apiDocs.sources.inventory",
      slug: "rspace-inventory",
      url: "https://example.com/resources/rspace_api_inventory_specs_2_24_0.yaml",
    });
    // Only the ELN spec is the default document.
    expect(inventory).not.toHaveProperty("default", true);
  });

  test("resolves relative servers against the deployment base", () => {
    expect(config.baseServerURL).toBe("https://example.com");
  });

  test("prefers API key auth and prefills both OAuth schemes", () => {
    expect(config.authentication?.preferredSecurityScheme).toBe("ApiKeySecurity");
    const schemes = config.authentication?.securitySchemes as Record<
      string,
      { flows: { password: Record<string, unknown> } }
    >;
    for (const name of ["OAuth", "OAuthJWT"]) {
      expect(schemes[name].flows.password).toMatchObject({
        "x-scalar-client-id": "rsInventoryWebClient",
        clientSecret: "rsInventoryPublicSecret",
        selectedScopes: ["all"],
      });
    }
  });

  test("hides the client button and avoids external fonts", () => {
    expect(config.hideClientButton).toBe(true);
    expect(config.withDefaultFonts).toBe(false);
  });

  test("opens all tags by default", () => {
    expect(config.defaultOpenAllTags).toBe(true);
  });

  test("disables the MCP generation button", () => {
    expect(config.mcp?.disabled).toBe(true);
  });

  test("disables the Ask AI agent on every source", () => {
    for (const source of config.sources ?? []) {
      expect(source.agent?.disabled).toBe(true);
    }
  });

  test("hides the Powered by Scalar attribution via custom CSS", () => {
    expect(config.customCss).toContain("darklight-reference");
    expect(config.customCss).toContain("display: none");
  });
});

describe("ApiDocsPage", () => {
  test("renders Scalar with a configuration containing both specs", () => {
    render(<ApiDocsPage />);
    const mounted = screen.getByTestId("mock-scalar");
    const passed = JSON.parse(mounted.getAttribute("data-configuration") ?? "{}") as ReturnType<
      typeof createApiDocsConfiguration
    >;
    expect(passed.sources?.map((s) => s.title)).toEqual([
      "common:apiDocs.sources.eln",
      "common:apiDocs.sources.inventory",
    ]);
  });
});
