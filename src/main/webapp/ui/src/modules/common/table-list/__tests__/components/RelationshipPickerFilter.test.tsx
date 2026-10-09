import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { TableList } from "@/modules/common/table-list/TableList";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "test-token" }),
}));

type BookableItem = { id: string; target: string };

const config = resolveCollectionConfig<BookableItem>({
  slug: "bookable-items",
  idField: "id",
  labels: { singularKey: "tableList.examples.record", pluralKey: "tableList.examples.records" },
  useAsTitle: "target",
  defaultColumns: ["target"],
  fields: [
    { name: "id", labelKey: "tableList.examples.fields.id", type: "text", list: false },
    {
      name: "target",
      labelKey: "tableList.examples.fields.title",
      type: "relationship",
      relationTo: "instruments",
      hasMany: false,
    },
  ],
});

function listInstruments(onRequest?: (url: URL) => void) {
  server.use(
    http.get("/api/v2/instruments", ({ request }) => {
      onRequest?.(new URL(request.url));
      return HttpResponse.json({
        docs: [{ id: 123, name: "Confocal microscope", globalId: "IN123" }],
        totalDocs: 1,
        limit: 20,
        page: 1,
        pagingCounter: 1,
        totalPages: 1,
        hasPrevPage: false,
        hasNextPage: false,
        prevPage: null,
        nextPage: null,
      });
    }),
  );
}

function renderFilters(onChange: (value: unknown) => void) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <TableList
        queryString={false}
        config={config}
        rows={[]}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: { search: "", expression: null }, onChange },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />
    </QueryClientProvider>,
  );
}

describe("relationship filter picker", () => {
  it("searches by name and applies the selected global ID", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const requests: URL[] = [];
    listInstruments((url) => requests.push(url));
    renderFilters(onChange);

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await user.type(screen.getByRole("combobox", { name: "common:tableList.filters.value" }), "Conf");

    const option = await screen.findByRole("option", { name: /Confocal microscope/ });
    // Following a link from the listbox would leave the page and lose the unsaved filter.
    expect(within(option).getByText("IN123")).toBeVisible();
    expect(within(option).queryByRole("link")).not.toBeInTheDocument();
    await user.click(option);
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));

    expect(onChange).toHaveBeenCalledWith({
      search: "",
      expression: { kind: "comparison", field: "target", operator: "equals", value: "IN123" },
    });
    expect(requests.map((url) => url.searchParams.get("where"))).toContain("name=contains=Conf");
    expect(requests[0]?.searchParams.get("fields[instruments]")).toBe("id,name,globalId");
    expect(requests[0]?.searchParams.get("limit")).toBe("20");
  });
});
