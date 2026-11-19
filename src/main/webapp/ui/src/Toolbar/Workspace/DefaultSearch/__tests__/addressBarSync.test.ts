import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { installAddressBarSync } from "../addressBarSync";

const searchSettings = {
  searchMode: true,
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
  pageNumber: 2,
};

const listShown = () => window.dispatchEvent(new CustomEvent("workspaceRecordListShown"));

describe("installAddressBarSync", () => {
  let uninstall: () => void;

  beforeEach(() => {
    window.history.replaceState(null, "", "/workspace");
    uninstall = installAddressBarSync();
  });

  afterEach(() => {
    uninstall();
    delete (window as { workspaceSettings?: unknown }).workspaceSettings;
  });

  test("a search writes the search to the address bar", () => {
    Object.assign(window, { workspaceSettings: { ...searchSettings } });
    listShown();
    expect(window.location.pathname + window.location.search).toBe(
      "/workspace?options%5B%5D=name&terms%5B%5D=Zebrafish&advancedSearch=false&operator=AND&sortOrder=DESC",
    );
  });

  test("leaving search mode clears the search from the address bar", () => {
    window.history.replaceState(null, "", "/workspace?options%5B%5D=name&terms%5B%5D=Zebrafish");
    Object.assign(window, { workspaceSettings: { ...searchSettings, searchMode: false } });
    listShown();
    expect(window.location.pathname + window.location.search).toBe("/workspace");
  });

  test("a folder listing leaves an address bar without a search alone", () => {
    window.history.replaceState(null, "", "/workspace/123?settingsKey=abc");
    Object.assign(window, { workspaceSettings: { ...searchSettings, searchMode: false } });
    listShown();
    expect(window.location.pathname + window.location.search).toBe("/workspace/123?settingsKey=abc");
  });
});
