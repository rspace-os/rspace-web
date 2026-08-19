import { describe, expect, it } from "vitest";
import { parseTableViewState, serializeTableViewState } from "../tableViewState";
import { config } from "./fixtures/tableListFixtures";

describe("table-list persisted view state", () => {
  it("round-trips non-default filters, columns, and sorting", () => {
    const values = {
      search: "Ada",
      where: { kind: "comparison", field: "owner", operator: "equals", value: "Ada" } as const,
      columns: ["title", "score"] as const,
      sort: [
        { field: "owner", direction: "asc" },
        { field: "score", direction: "desc" },
      ] as const,
    };

    const serialized = serializeTableViewState(values, config);

    expect(serialized).not.toBeNull();
    expect(parseTableViewState(serialized as string, config)).toEqual(values);
  });

  it("elides configured defaults and removes an entirely default view", () => {
    expect(
      serializeTableViewState(
        {
          search: "",
          where: null,
          columns: config.defaultColumns,
          sort: config.defaultSort ?? [],
        },
        config,
      ),
    ).toBeNull();

    expect(
      JSON.parse(
        serializeTableViewState(
          {
            search: "Ada",
            where: null,
            columns: config.defaultColumns,
            sort: config.defaultSort ?? [],
          },
          config,
        ) as string,
      ),
    ).toEqual({ v: 1, search: "Ada", where: null, columns: null, sort: null });
  });

  it("rejects malformed and unsupported envelopes", () => {
    expect(parseTableViewState("not-json", config)).toBeNull();
    expect(
      parseTableViewState(JSON.stringify({ v: 2, search: null, where: null, columns: null, sort: null }), config),
    ).toBeNull();
    expect(parseTableViewState(JSON.stringify({ v: 1, search: null, where: null, columns: null }), config)).toBeNull();
    expect(
      parseTableViewState(JSON.stringify({ v: 1, search: null, where: null, columns: 4, sort: null }), config),
    ).toBeNull();
  });

  it("falls back stale members independently and preserves valid siblings", () => {
    expect(
      parseTableViewState(
        JSON.stringify({
          v: 1,
          search: "Ada",
          where: "owner==Ada",
          columns: JSON.stringify({ fields: ["title", "removedField"] }),
          sort: "title,-score",
          ignored: true,
        }),
        config,
      ),
    ).toEqual({
      search: "Ada",
      where: { kind: "comparison", field: "owner", operator: "equals", value: "Ada" },
      columns: config.defaultColumns,
      sort: [
        { field: "title", direction: "asc" },
        { field: "score", direction: "desc" },
      ],
    });

    expect(
      parseTableViewState(
        JSON.stringify({
          v: 1,
          search: null,
          where: "removedField==value",
          columns: JSON.stringify({ fields: ["title", "score"] }),
          sort: "removedField",
        }),
        config,
      ),
    ).toEqual({
      search: null,
      where: null,
      columns: ["title", "score"],
      sort: config.defaultSort,
    });
  });
});
