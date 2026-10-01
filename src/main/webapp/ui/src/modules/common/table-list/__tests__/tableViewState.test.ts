import { afterEach, describe, expect, it } from "vitest";
import { parseTableViewState, serializeTableViewState } from "../tableViewState";
import { tableViewStorageKey } from "../tableViewStorage";
import { savedViewSelectors } from "../useTableListQueryString";
import { config } from "./fixtures/tableListFixtures";

describe("saved view field names", () => {
  const key = tableViewStorageKey("records");

  afterEach(() => {
    window.localStorage.clear();
    window.history.replaceState({}, "", "/");
  });

  it("reads the names a stored view refers to, so they can be hydrated by ID", () => {
    window.localStorage.setItem(
      key,
      JSON.stringify({
        v: 1,
        search: null,
        where: 'owner==Ada;customFields.SF104=="BSL-2"',
        columns: JSON.stringify({ fields: ["title", "customFields.SF108"] }),
        sort: null,
      }),
    );

    expect(savedViewSelectors("records", true)).toEqual(["owner", "customFields.SF104", "title", "customFields.SF108"]);
  });

  it("prefers the URL, because that is what the restore actually applies", () => {
    window.localStorage.setItem(key, JSON.stringify({ v: 1, search: null, where: "owner==Stored", columns: null }));
    window.history.replaceState({}, "", "/?records.where=customFields.SF9%3D%3DShared");

    expect(savedViewSelectors("records", true)).toEqual(["customFields.SF9"]);
  });
});

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
          defaults: JSON.stringify({ fields: config.defaultColumns }),
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

describe("a saved column selection when the configured defaults change", () => {
  const view = (fields: readonly string[], defaults?: readonly string[]) =>
    JSON.stringify({
      v: 1,
      search: null,
      where: null,
      columns: JSON.stringify({ fields }),
      ...(defaults ? { defaults: JSON.stringify({ fields: defaults }) } : {}),
      sort: null,
    });

  it("records the defaults it was saved against", () => {
    const serialized = serializeTableViewState(
      { search: null, where: null, columns: ["title", "score"], sort: config.defaultSort ?? [] },
      config,
    );

    expect(JSON.parse(serialized as string).defaults).toBe(JSON.stringify({ fields: config.defaultColumns }));
  });

  it("shows a default column added since the view was saved, after the default before it", () => {
    // The view predates "score" becoming a default, so the saved selection never chose to hide it.
    const values = parseTableViewState(view(["title", "enabled"], ["title", "owner", "enabled", "modifiedAt"]), config);

    expect(values?.columns).toEqual(["title", "score", "enabled"]);
  });

  it("keeps a default hidden that the user removed after seeing it", () => {
    const values = parseTableViewState(view(["title", "score"], config.defaultColumns), config);

    expect(values?.columns).toEqual(["title", "score"]);
  });

  it("gives a view saved before defaults were recorded every missing default once", () => {
    const values = parseTableViewState(view(["title", "score"]), config);

    expect(values?.columns).toEqual(["title", "owner", "score", "enabled", "modifiedAt"]);
  });
});

describe("a saved view naming a field that no longer exists", () => {
  const saved = JSON.stringify({
    v: 1,
    search: null,
    where: 'owner=="Ada";customFields.SF104=="BSL-2"',
    columns: JSON.stringify(["title", "customFields.SF104", "score"]),
    defaults: JSON.stringify({ fields: config.defaultColumns }),
    sort: null,
  });
  const stale = (dropped: string[]) => ({
    isStale: (name: string) => name.startsWith("customFields."),
    onDropped: (name: string) => dropped.push(name),
  });

  it("discards the whole view when nothing is declared droppable", () => {
    const values = parseTableViewState(saved, config);

    expect(values?.where).toBeNull();
    expect(values?.columns).toEqual(config.defaultColumns);
  });

  it("drops only the stale filter and column and keeps the valid siblings", () => {
    const dropped: string[] = [];

    const values = parseTableViewState(saved, config, stale(dropped));

    expect(values?.where).toEqual({ kind: "comparison", field: "owner", operator: "equals", value: "Ada" });
    expect(values?.columns).toEqual(["title", "score"]);
    expect(dropped).toEqual(["customFields.SF104", "customFields.SF104"]);
  });
});
