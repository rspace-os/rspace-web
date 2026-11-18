import { describe, expect, test } from "vitest";
import { parseSearchQuery, summariseSearch, toSearchQuery } from "../workspaceSearchUrl";

const baseSettings = {
  options: ["name"],
  terms: ["Zebrafish"],
  advancedSearch: false,
  operator: "AND",
  sharedFilter: false,
  favoritesFilter: false,
  viewableItemsFilter: false,
  templatesFilter: false,
  ontologiesFilter: false,
  orderBy: null,
  sortOrder: "DESC",
};

describe("toSearchQuery", () => {
  test("a simple search serialises with percent-encoded brackets", () => {
    expect(toSearchQuery(baseSettings)).toBe(
      "options%5B%5D=name&terms%5B%5D=Zebrafish&advancedSearch=false&operator=AND&sortOrder=DESC",
    );
  });

  test("an advanced search keeps every criterion, active filters and the sort", () => {
    expect(
      toSearchQuery({
        ...baseSettings,
        options: ["name", "tag"],
        terms: ["Zebrafish", "bkmktag"],
        advancedSearch: true,
        operator: "OR",
        favoritesFilter: true,
        ontologiesFilter: true,
        orderBy: "name",
        sortOrder: "ASC",
      }),
    ).toBe(
      "options%5B%5D=name&options%5B%5D=tag&terms%5B%5D=Zebrafish&terms%5B%5D=bkmktag" +
        "&advancedSearch=true&operator=OR&favoritesFilter=true&ontologiesFilter=true&orderBy=name&sortOrder=ASC",
    );
  });

  test("listing state that is not part of the search is left out", () => {
    const query = toSearchQuery({
      ...baseSettings,
      pageNumber: 3,
      resultsPerPage: 50,
      currentViewMode: "LIST_VIEW",
      parentFolderId: 12,
      grandparentFolderId: 4,
      settingsKey: "abc",
    } as typeof baseSettings);
    for (const key of [
      "pageNumber",
      "resultsPerPage",
      "currentViewMode",
      "parentFolderId",
      "grandparentFolderId",
      "settingsKey",
    ]) {
      expect(new URLSearchParams(query).has(key)).toBe(false);
    }
  });

  test("searches that differ only in sort order give different strings", () => {
    expect(toSearchQuery({ ...baseSettings, sortOrder: "ASC" })).not.toBe(toSearchQuery(baseSettings));
  });
});

describe("parseSearchQuery", () => {
  test("a canonical query parses back to the same query", () => {
    const query = toSearchQuery({
      ...baseSettings,
      options: ["fullText", "owner", "created"],
      terms: ['say "hi" & 1+1 <script>', "user1a<<>>user2b", "2024-01-01T00:00:00.000Z; null"],
      advancedSearch: true,
      operator: "OR",
      sharedFilter: true,
    });
    const parsed = parseSearchQuery(`?${query}`);
    if (parsed.kind !== "search") throw new Error(`expected a search, got ${parsed.kind}`);
    expect(toSearchQuery(parsed.state)).toBe(query);
  });

  test.each(["", "?", "?settingsKey=abc", "?operator=OR&sortOrder=ASC"])("%j holds no search", (search) => {
    expect(parseSearchQuery(search)).toEqual({ kind: "none" });
  });

  test.each([
    ["more options than terms", "?options[]=name&options[]=tag&terms[]=x"],
    ["more terms than options", "?options[]=name&terms[]=x&terms[]=y"],
    ["terms with no options", "?terms[]=x"],
    ["options with no terms", "?options[]=name"],
    ["an unknown option", "?options[]=chemical&terms[]=x"],
  ])("%s is invalid", (_, search) => {
    expect(parseSearchQuery(search)).toEqual({ kind: "invalid" });
  });

  test("unparseable operator, sort order and flags fall back to the defaults", () => {
    expect(
      parseSearchQuery("?options[]=name&terms[]=x&operator=XOR&sortOrder=up&advancedSearch=yes&sharedFilter=1"),
    ).toEqual({
      kind: "search",
      state: { ...baseSettings, terms: ["x"] },
    });
  });
});

describe("summariseSearch", () => {
  const labels = {
    options: {
      global: "All",
      fullText: "Text",
      tag: "Tag(s)",
      name: "Name",
      created: "Creation date",
      lastModified: "Last modified",
      form: "Form",
      template: "Template",
      attachment: "Attachment",
      owner: "Owner(s)",
      records: "Within records",
    },
    filters: {
      sharedFilter: "Shared with me",
      favoritesFilter: "Favorites",
      viewableItemsFilter: "View all",
      templatesFilter: "Templates",
      ontologiesFilter: "Ontology files",
    },
    operators: { AND: "AND", OR: "OR" },
    dateRange: (from: string | null, to: string | null) => `${from ?? "any"} to ${to ?? "any"}`,
  };

  test("a simple search names its option and term", () => {
    expect(summariseSearch(baseSettings, labels)).toBe("Name: Zebrafish");
  });

  test("advanced criteria are joined by the operator", () => {
    expect(
      summariseSearch(
        { ...baseSettings, options: ["name", "tag"], terms: ["Zebrafish", "bkmktag"], operator: "OR" },
        labels,
      ),
    ).toBe("Name: Zebrafish OR Tag(s): bkmktag");
  });

  test("dates show as a range of days", () => {
    expect(
      summariseSearch({ ...baseSettings, options: ["created"], terms: ["2024-01-01T00:00:00.000Z; null"] }, labels),
    ).toBe("Creation date: 2024-01-01 to any");
  });

  test("owner and tag separators show as commas", () => {
    expect(
      summariseSearch(
        {
          ...baseSettings,
          options: ["owner", "tag"],
          terms: ["user1a<<>>user2b", "red__rspactags_comma__blue"],
        },
        labels,
      ),
    ).toBe("Owner(s): user1a, user2b AND Tag(s): red, blue");
  });

  test("active filters follow the criteria", () => {
    expect(summariseSearch({ ...baseSettings, favoritesFilter: true, sharedFilter: true }, labels)).toBe(
      "Name: Zebrafish; Shared with me, Favorites",
    );
  });
});
