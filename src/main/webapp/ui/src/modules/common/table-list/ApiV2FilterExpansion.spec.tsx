import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import {
  apiV2Page,
  bookingCataloguePage,
  runtimeCatalog,
  runtimeField,
} from "./__tests__/mocks/apiV2FilterExpansionMocks";
import { ApiV2FilterExpansionStory } from "./ApiV2FilterExpansion.story";
import { ApiV2FilterExpansionPage } from "./pageObjects/ApiV2FilterExpansionPage";
import { tableViewStorageKey } from "./tableViewStorage";

const view = new ApiV2FilterExpansionPage();
const instruments = Array.from({ length: 10 }, (_, index) => ({ id: index + 41, name: `Narrow asset ${index + 1}` }));
const filterCode = runtimeField("Filter code");
const storageZone = runtimeField("Storage zone");
const runtimeDefinitions = [filterCode, storageZone];

let collectionRequests: string[];
let catalogRequests: URLSearchParams[];

function latestWhere(): string | null {
  const url = collectionRequests.at(-1);
  return url ? new URL(url).searchParams.get("where") : null;
}

function latestCollectionRequest(): string {
  const url = collectionRequests.at(-1);
  if (!url) throw new Error("No booking configuration request has been captured");
  return url;
}

async function renderStory(narrow = false): Promise<void> {
  render(<ApiV2FilterExpansionStory narrow={narrow} />);
  await expect.element(view.table).toBeVisible();
}

async function chooseRuntimeField(name: string): Promise<void> {
  await view.chooseField(1, /Custom field/);
  const search = view.runtimeFieldSearch(1);
  await userEvent.fill(search, name);
  await userEvent.click(page.getByRole("option", { name: new RegExp(name, "i") }));
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
  window.localStorage.clear();
  collectionRequests = [];
  catalogRequests = [];
  worker.use(
    http.get("/api/v2/booking-configurations", ({ request }) => {
      collectionRequests.push(request.url);
      return HttpResponse.json(apiV2Page());
    }),
    http.get("/api/v2/instruments/fields/extraFields", ({ request }) => {
      const url = new URL(request.url);
      catalogRequests.push(url.searchParams);
      const ids = new Set((url.searchParams.get("ids") ?? "").split(",").filter(Boolean));
      const search = url.searchParams.get("search")?.toLocaleLowerCase();
      const fields = runtimeDefinitions.filter((field) =>
        ids.size > 0
          ? ids.has(field.id)
          : (search ?? "").length >= 2 && field.label.toLocaleLowerCase().includes(search ?? ""),
      );
      return HttpResponse.json(runtimeCatalog(fields));
    }),
    http.get("/api/v2/booking-catalogue", ({ request }) => {
      const url = new URL(request.url);
      const query = url.searchParams.get("q")?.toLocaleLowerCase();
      const target = url.searchParams.get("target")?.toLocaleUpperCase();
      const matches = instruments.filter(({ id, name }) =>
        target ? `IN${id}` === target : (query ?? "").length >= 2 && name.toLocaleLowerCase().includes(query ?? ""),
      );
      return HttpResponse.json(bookingCataloguePage(matches));
    }),
  );
});

afterEach(() => {
  cleanup();
  window.history.replaceState(null, "", "/");
  window.localStorage.clear();
});

describe("API v2 filter expansion", () => {
  test("uses an equals operator for a direct select and filters a relationship by selection or pasted ID", async () => {
    await renderStory();
    await view.openFilters();
    await view.addFilter();
    await view.addFilter();
    await view.addFilter();
    // The lifecycle state is labelled Status, as its column header is.
    await view.chooseField(1, "Status");
    await view.chooseField(2, "Bookable item");
    await view.chooseField(3, "Bookable item → Name");

    await expect.element(view.operator(1)).toHaveValue("equals");
    await userEvent.click(view.value(1));
    await userEvent.click(page.getByRole("option", { name: "ACTIVE" }));

    await view.selectRelationshipOptionWithKeyboard("Narrow asset 1", 2);

    await userEvent.fill(view.scalarValue(3), "Narrow asset");
    await userEvent.keyboard("{Escape}");
    await view.applyFilters();
    await expect
      .poll(latestWhere)
      .toSatisfy(
        (where: string | null) =>
          (where ?? "").includes("state==ACTIVE") &&
          (where ?? "").includes("target==IN41") &&
          (where ?? "").includes('target.name=="Narrow asset"'),
      );

    await view.openFilters();
    await view.selectRelationshipOptionWithKeyboard("Narrow asset 2", 2, "IN42");
    await view.applyFilters();
    await expect.poll(latestWhere).toContain("target==IN42");
  });

  test("selects a runtime field, safely encodes its value, and restores the saved filter after remount", async () => {
    await renderStory();
    await view.openFilters();
    await view.addFilter();
    await chooseRuntimeField("Filter code");
    const value = 'A / unit, "west"';
    await userEvent.fill(view.scalarValue(1), value);
    await userEvent.keyboard("{Escape}");
    await view.applyFilters();

    await expect.poll(latestWhere).toContain(`target.extraFields.${filterCode.id}`);
    const firstRequest = latestCollectionRequest();
    expect(firstRequest).not.toContain(value);
    expect(new URL(firstRequest).search).toContain("%2F");
    const storageKey = tableViewStorageKey("api-v2-filter-expansion-story");
    expect(window.localStorage.getItem(storageKey)).toContain(filterCode.id);

    window.history.replaceState(null, "", "/");
    cleanup();
    collectionRequests = [];
    catalogRequests = [];
    await renderStory();
    await expect.poll(latestWhere).toContain(`target.extraFields.${filterCode.id}`);
    expect(catalogRequests.some((params) => params.get("ids") === filterCode.id)).toBe(true);
  });

  test("keeps a narrow relationship multi-select scrollable and supports keyboard selection", async () => {
    await renderStory(true);
    await view.openFilters();
    await view.addFilter();
    await view.chooseField(1, "Bookable item");
    await view.chooseOperator(1, "is one of");

    for (const instrument of instruments) {
      await view.selectRelationshipOptionWithKeyboard(instrument.name);
    }

    await expect.poll(() => view.relationChipsScrollable()).toBe(true);
    for (const instrument of instruments) {
      await expect.element(page.getByRole("button", { name: `Remove ${instrument.name}`, exact: true })).toBeVisible();
    }
  });

  test("shows runtime catalog loading, no-match, failure, and malformed response states", async () => {
    let finishLoading: (() => void) | undefined;
    worker.use(
      http.get("/api/v2/instruments/fields/extraFields", ({ request }) => {
        const search = new URL(request.url).searchParams.get("search") ?? "";
        if (search === "Blocked") return new HttpResponse(null, { status: 503 });
        if (search === "Malformed") return HttpResponse.json({ fields: [{ id: 7 }] });
        if (search === "Loading") {
          return new Promise<Response>((resolve) => {
            finishLoading = () => resolve(HttpResponse.json(runtimeCatalog([runtimeField("Loading field")])));
          });
        }
        return HttpResponse.json(runtimeCatalog([]));
      }),
    );
    await renderStory();
    await view.openFilters();
    await view.addFilter();
    await view.chooseField(1, /Custom field/);
    const search = view.runtimeFieldSearch(1);
    await userEvent.click(search);
    await expect.element(page.getByText("Type 2 characters to search")).toBeVisible();

    await userEvent.fill(search, "Unknown field");
    await expect.element(page.getByText("No matching custom field.")).toBeVisible();

    await userEvent.fill(search, "Blocked");
    await expect.element(page.getByText("Custom fields could not be searched.")).toBeVisible();

    await userEvent.fill(search, "Malformed");
    await expect.element(page.getByText("Custom fields could not be searched.")).toBeVisible();

    await userEvent.fill(search, "Loading");
    await expect.element(page.getByText("Searching…")).toBeVisible();
    await expect.poll(() => Boolean(finishLoading)).toBe(true);
    finishLoading?.();
    await expect.element(page.getByRole("option", { name: /Loading field/ })).toBeVisible();
  });

  test("restores saved runtime selectors in bounded catalog ID batches", async () => {
    const [first, second] = runtimeDefinitions;
    if (!first || !second) throw new Error("Expected two runtime field definitions");
    const storageKey = tableViewStorageKey("api-v2-filter-expansion-story");
    window.localStorage.setItem(
      storageKey,
      JSON.stringify({
        v: 1,
        search: null,
        where: `target.extraFields.${first.id}==north;target.extraFields.${second.id}==west`,
        columns: null,
        sort: null,
      }),
    );

    await renderStory();
    await expect.poll(() => catalogRequests.filter((params) => params.has("ids")).length).toBe(2);
    expect(catalogRequests.filter((params) => params.has("ids")).map((params) => params.get("ids"))).toEqual(
      expect.arrayContaining([first.id, second.id]),
    );
    await expect.poll(latestWhere).toContain(`target.extraFields.${first.id}`);
    await expect.poll(latestWhere).toContain(`target.extraFields.${second.id}`);
  });
});
