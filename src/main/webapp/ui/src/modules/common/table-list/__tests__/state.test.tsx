import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { describe, expect, it, vi } from "vitest";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { useTableList } from "../hooks/state";
import { TableList } from "../TableList";
import type { CollectionFetcher } from "../types";

type TestRecord = { id: string; title: string; owner: string; score: number };
const config = resolveCollectionConfig<TestRecord>({
  slug: "records",
  idField: "id",
  useAsTitle: "title",
  labels: { singularKey: "tableList.examples.record", pluralKey: "tableList.examples.records" },
  defaultColumns: ["title", "owner"],
  fields: [
    { name: "id", type: "text", labelKey: "tableList.examples.fields.id", list: false },
    { name: "title", type: "text", labelKey: "tableList.examples.fields.title" },
    { name: "owner", type: "text", labelKey: "tableList.examples.fields.owner" },
    { name: "score", type: "number", labelKey: "tableList.examples.fields.score" },
  ],
});

const records: readonly TestRecord[] = [{ id: "1", title: "Alpha", owner: "Ada", score: 8 }];

describe("useTableList", () => {
  it("lets surrounding components read and update remote table state", async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const fetch = vi.fn<CollectionFetcher<TestRecord>>(async () => ({ rows: records, rowCount: records.length }));

    function Harness() {
      const table = useTableList({
        config,
        dataSource: { type: "remote", queryKey: ["records"], fetch },
      });
      return (
        <>
          <button type="button" onClick={() => table.setSorting([{ field: "title", direction: "asc" }])}>
            {"Sort outside table"}
          </button>
          <output>{table.state.sorting.map((rule) => `${rule.field}:${rule.direction}`).join(",")}</output>
          <TableList {...table.tableProps} />
        </>
      );
    }

    render(
      <QueryClientProvider client={queryClient}>
        <NuqsTestingAdapter hasMemory>
          <Harness />
        </NuqsTestingAdapter>
      </QueryClientProvider>,
    );

    await screen.findByRole("cell", { name: "Alpha" });
    expect(queryClient.getQueryCache().findAll({ queryKey: ["records"] })[0]?.meta).toEqual(
      navigationProgressQueryMeta,
    );
    await user.click(screen.getByRole("button", { name: "Sort outside table" }));

    expect(screen.getByText("title:asc")).toBeVisible();
    await waitFor(() =>
      expect(fetch).toHaveBeenLastCalledWith(
        expect.objectContaining({ sorting: [{ field: "title", direction: "asc" }] }),
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      ),
    );
  });

  it("uses a remote data source's semantic query key", async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const fetch = vi.fn<CollectionFetcher<TestRecord>>(async () => ({ rows: records, rowCount: records.length }));

    function Harness() {
      const table = useTableList({
        config,
        initialState: { visibleFields: ["title", "owner"] },
        dataSource: {
          type: "remote",
          queryKey: (state) => ["records", [...state.visibleFields].sort().join(",")],
          fetch,
        },
      });
      return (
        <>
          <button type="button" onClick={() => table.setVisibleFields(["owner", "title"])}>
            {"Reorder fields"}
          </button>
          <button type="button" onClick={() => table.setVisibleFields(["owner", "title", "score"])}>
            {"Add field"}
          </button>
          <output>{table.state.visibleFields.join(",")}</output>
        </>
      );
    }

    render(
      <QueryClientProvider client={queryClient}>
        <Harness />
      </QueryClientProvider>,
    );

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole("button", { name: "Reorder fields" }));
    expect(screen.getByRole("status")).toHaveTextContent("owner,title");
    expect(fetch).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "Add field" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  });
});
