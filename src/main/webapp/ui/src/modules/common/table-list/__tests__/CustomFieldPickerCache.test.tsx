import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { OAUTH_TOKEN, oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import type { RuntimeNamespaceSummary } from "@/modules/common/collection/config";
import { queryKeys } from "@/modules/common/hooks/auth";
import type { RuntimeFieldDefinition } from "@/modules/common/table-list/api-v2/runtimeFieldCatalog";
import { CustomFieldPicker } from "@/modules/common/table-list/components/CustomFieldPicker";

const searchLabel = "Search custom fields";

function source(catalog: string): RuntimeNamespaceSummary {
  return {
    namespace: "customFields",
    viaLabel: "Instrument",
    catalog,
    maximumLimit: 200,
    filterable: true,
    columnSelectable: true,
  };
}

function definition(id: string, label: string): RuntimeFieldDefinition {
  return {
    id,
    selector: `customFields.${id}`,
    label,
    type: "text",
    jsonType: "string",
    operators: ["=="],
    supportsWildcards: false,
    columnSelectable: true,
    sortable: false,
    source: { id: "template", label: "Template" },
    options: [],
  };
}

function catalogPage(field: RuntimeFieldDefinition) {
  return { fields: [field], hasMore: false, page: 1, limit: 20 };
}

function picker(queryClient: QueryClient, sources: readonly RuntimeNamespaceSummary[]) {
  return (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback="Loading custom fields">
        <CustomFieldPicker
          sources={sources}
          ariaLabel={searchLabel}
          chosenLabel={undefined}
          known={[]}
          onSelect={() => {}}
        />
      </Suspense>
    </QueryClientProvider>
  );
}

function createQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

describe("CustomFieldPicker catalog cache", () => {
  it("loads the new catalog for the same namespace without showing old options while it is pending", async () => {
    const user = userEvent.setup();
    const client = createQueryClient();
    const catalogA = source("/api/v2/instruments/fields/customFields");
    const catalogB = source("/api/v2/instruments/fields/extraFields");
    let allowCatalogBResponse: () => void = () => {};
    const catalogBPending = new Promise<void>((resolve) => {
      allowCatalogBResponse = resolve;
    });

    server.use(
      oauthTokenHandler(true),
      http.get(catalogA.catalog, () => HttpResponse.json(catalogPage(definition("A1", "Catalog A field")))),
      http.get(catalogB.catalog, async () => {
        await catalogBPending;
        return HttpResponse.json(catalogPage(definition("B1", "Catalog B field")));
      }),
    );

    const { rerender } = render(picker(client, [catalogA]));
    try {
      const input = await screen.findByRole("combobox", { name: searchLabel });
      await user.click(input);
      await user.type(input, "alpha");
      const optionA = await screen.findByRole("option");
      expect(within(optionA).getByText("Catalog A field")).toBeVisible();

      rerender(picker(client, [catalogB]));

      await screen.findByRole("combobox", { name: searchLabel, busy: true });
      expect(screen.queryByText("Catalog A field")).not.toBeInTheDocument();

      allowCatalogBResponse();
      const optionB = await screen.findByRole("option");
      expect(within(optionB).getByText("Catalog B field")).toBeVisible();
    } finally {
      allowCatalogBResponse();
    }
  });

  it("loads suggestions again when the OAuth token changes for the same catalog", async () => {
    const user = userEvent.setup();
    const client = createQueryClient();
    const catalog = source("/api/v2/instruments/fields/customFields");
    const authorizationHeaders: string[] = [];

    server.use(
      oauthTokenHandler(true),
      http.get(catalog.catalog, ({ request }) => {
        const authorization = request.headers.get("Authorization") ?? "";
        authorizationHeaders.push(authorization);
        const rotated = authorization === "Bearer rotated-session-token";
        const field = rotated ? definition("B1", "Token B field") : definition("A1", "Token A field");
        return HttpResponse.json(catalogPage(field));
      }),
    );

    render(picker(client, [catalog]));
    const input = await screen.findByRole("combobox", { name: searchLabel });
    await user.click(input);
    await user.type(input, "alpha");
    const optionA = await screen.findByRole("option");
    expect(within(optionA).getByText("Token A field")).toBeVisible();

    server.use(http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "rotated-session-token" })));
    await act(async () => {
      await client.invalidateQueries({ queryKey: queryKeys.oauthToken(true) });
    });

    await screen.findByText("Token B field");
    const optionB = screen.getByRole("option");
    expect(within(optionB).getByText("Token B field")).toBeVisible();
    expect(screen.queryByText("Token A field")).not.toBeInTheDocument();
    expect(authorizationHeaders).toEqual([`Bearer ${OAUTH_TOKEN}`, "Bearer rotated-session-token"]);
  });
});
