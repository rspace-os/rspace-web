import { type APIRequestContext, expect, request } from "@playwright/test";
import { parse } from "@rsql/parser";
import { env } from "@/__tests__/e2e/env";
import { test as base } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN, USERS } from "@/__tests__/e2e/users";

type CreatedInstrument = { id: number; globalId: string; name: string; sharingMode?: string };
type BookingConfiguration = { id: number };
type BookingConfigurationPage = {
  docs: Array<{ id: number }>;
  totalDocs: number;
  limit: number;
  page: number;
  totalPages: number;
};
type InventoryAccessView = { permittedActions: string[] };
type RuntimeFieldCatalog = { fields: Array<{ id: string; label: string }> };
type FilterExpansionRecords = {
  configurations: Array<{ id: number; apiKey: string }>;
  instruments: Array<{ id: number; apiKey: string }>;
};

type FilterExpansionFixtures = {
  filterExpansionRecords: FilterExpansionRecords;
};

const CLEANUP_TIMEOUT_MS = 120_000;
const CLEANUP_REQUEST_TIMEOUT_MS = 10_000;

async function createInstrument(
  apiContext: APIRequestContext,
  apiKey: string,
  name: string,
  extraFieldName: string,
  extraFieldValue: string,
  privateRecord = false,
): Promise<CreatedInstrument> {
  const response = await apiContext.post("/api/inventory/v1/instruments", {
    headers: { apiKey },
    data: {
      name,
      extraFields: [{ name: extraFieldName, type: "text", content: extraFieldValue }],
      ...(privateRecord ? { sharingMode: "OWNER_ONLY" } : {}),
    },
  });
  expect(response.ok(), await response.text()).toBe(true);
  return response.json() as Promise<CreatedInstrument>;
}

async function createBookingConfiguration(
  apiContext: APIRequestContext,
  apiKey: string,
  instrumentId: number,
): Promise<BookingConfiguration> {
  const response = await apiContext.post("/api/v2/booking-configurations", {
    headers: { apiKey },
    data: {
      enabled: true,
      target: { relationTo: "booking-instruments", value: instrumentId },
    },
  });
  expect(response.ok(), await response.text()).toBe(true);
  return response.json() as Promise<BookingConfiguration>;
}

async function cleanupFilterExpansionRecords({ configurations, instruments }: FilterExpansionRecords): Promise<void> {
  const cleanupErrors: string[] = [];
  let cleanupContext: APIRequestContext | undefined;

  try {
    cleanupContext = await request.newContext({
      baseURL: env.baseURL,
      storageState: { cookies: [], origins: [] },
      timeout: CLEANUP_REQUEST_TIMEOUT_MS,
    });
  } catch (error) {
    cleanupErrors.push(`Could not create cleanup request context: ${String(error)}`);
  }

  if (cleanupContext) {
    try {
      for (const { id } of configurations.toReversed()) {
        try {
          const current = await cleanupContext.get(`/api/v2/booking-configurations/${id}`, {
            headers: { apiKey: SYSADMIN.apiKey },
          });
          if (!current.ok()) {
            cleanupErrors.push(`Could not read booking configuration ${id}: ${await current.text()}`);
            continue;
          }
          const etag = current.headers().etag;
          if (!etag) {
            cleanupErrors.push(`Booking configuration ${id} did not return an ETag`);
            continue;
          }
          const response = await cleanupContext.delete(`/api/v2/booking-configurations/${id}?permanent=true`, {
            headers: { apiKey: SYSADMIN.apiKey, "If-Match": etag },
          });
          if (!response.ok()) {
            cleanupErrors.push(`Could not permanently delete booking configuration ${id}: ${await response.text()}`);
          }
        } catch (error) {
          cleanupErrors.push(`Booking configuration ${id}: ${String(error)}`);
        }
      }

      for (const { id, apiKey } of instruments.toReversed()) {
        try {
          const response = await cleanupContext.delete(`/api/inventory/v1/instruments/${id}`, {
            headers: { apiKey },
          });
          if (!response.ok()) cleanupErrors.push(`Could not delete instrument ${id}: ${await response.text()}`);
        } catch (error) {
          cleanupErrors.push(`Instrument ${id}: ${String(error)}`);
        }
      }
    } finally {
      try {
        await cleanupContext.dispose();
      } catch (error) {
        cleanupErrors.push(`Could not dispose cleanup request context: ${String(error)}`);
      }
    }
  }

  expect.soft(cleanupErrors, "all test-created records should be cleaned up").toEqual([]);
}

const test = base.extend<FilterExpansionFixtures>({
  filterExpansionRecords: [
    // biome-ignore lint/correctness/noEmptyPattern: Playwright requires destructuring pattern for fixture args
    async ({}, use) => {
      const records: FilterExpansionRecords = { configurations: [], instruments: [] };
      try {
        await use(records);
      } finally {
        await cleanupFilterExpansionRecords(records);
      }
    },
    { timeout: CLEANUP_TIMEOUT_MS },
  ],
});

test.describe("REST API v2 filter expansion", { tag: tags.INVENTORY }, () => {
  test("filters live bookable items by related identity and runtime field without exposing a private target", async ({
    apiContext,
    appUser,
    filterExpansionRecords,
    page,
    pageBookableItemsFilter,
  }) => {
    const fieldName = uniqueName("E2E filter expansion field");
    const fieldValue = `${uniqueName("E2E filter expansion match")} / "quoted, value"`;
    const matchingName = uniqueName("E2E filter expansion match instrument");
    const otherName = uniqueName("E2E filter expansion other instrument");
    const privateName = uniqueName("E2E filter expansion private instrument");
    const privateOwner = USERS.user7g;
    const { configurations, instruments } = filterExpansionRecords;
    const collectionRequests: string[] = [];
    const browserErrors: string[] = [];
    const matching = await createInstrument(apiContext, appUser.apiKey, matchingName, fieldName, fieldValue);
    instruments.push({ id: matching.id, apiKey: appUser.apiKey });
    const other = await createInstrument(apiContext, appUser.apiKey, otherName, fieldName, "other value");
    instruments.push({ id: other.id, apiKey: appUser.apiKey });
    const privateInstrument = await createInstrument(
      apiContext,
      privateOwner.apiKey,
      privateName,
      fieldName,
      fieldValue,
      true,
    );
    instruments.push({ id: privateInstrument.id, apiKey: privateOwner.apiKey });
    expect(privateInstrument.sharingMode).toBe("OWNER_ONLY");
    const privateInstrumentRead = await apiContext.get(`/api/inventory/v1/instruments/${privateInstrument.id}`, {
      headers: { apiKey: appUser.apiKey },
    });
    expect(privateInstrumentRead.ok(), await privateInstrumentRead.text()).toBe(true);
    const privateInstrumentView = (await privateInstrumentRead.json()) as InventoryAccessView;
    expect(privateInstrumentView.permittedActions).not.toContain("READ");

    const matchingConfiguration = await createBookingConfiguration(apiContext, appUser.apiKey, matching.id);
    configurations.push({ id: matchingConfiguration.id, apiKey: appUser.apiKey });
    const otherConfiguration = await createBookingConfiguration(apiContext, appUser.apiKey, other.id);
    configurations.push({ id: otherConfiguration.id, apiKey: appUser.apiKey });
    const privateConfiguration = await createBookingConfiguration(
      apiContext,
      privateOwner.apiKey,
      privateInstrument.id,
    );
    configurations.push({ id: privateConfiguration.id, apiKey: privateOwner.apiKey });

    const fixtureIds = [matchingConfiguration.id, otherConfiguration.id, privateConfiguration.id];
    const initialResponse = await apiContext.get(
      `/api/v2/booking-configurations?where=id=in=(${fixtureIds.join(",")})`,
      { headers: { apiKey: appUser.apiKey } },
    );
    expect(initialResponse.ok(), await initialResponse.text()).toBe(true);
    const initial = (await initialResponse.json()) as { docs: Array<{ id: number }> };
    expect(initial.docs.map(({ id }) => id)).toContain(matchingConfiguration.id);
    expect(initial.docs.map(({ id }) => id)).toContain(otherConfiguration.id);
    expect(initial.docs.map(({ id }) => id)).not.toContain(privateConfiguration.id);

    const privateIdentityResponse = await apiContext.get(
      `/api/v2/booking-configurations?limit=100&where=target==${privateInstrument.globalId}`,
      { headers: { apiKey: appUser.apiKey } },
    );
    const unknownIdentityResponse = await apiContext.get(
      "/api/v2/booking-configurations?limit=100&where=target==IN9999999999999999",
      { headers: { apiKey: appUser.apiKey } },
    );
    expect(privateIdentityResponse.status()).toBe(unknownIdentityResponse.status());
    expect(privateIdentityResponse.ok(), await privateIdentityResponse.text()).toBe(true);
    expect(unknownIdentityResponse.ok(), await unknownIdentityResponse.text()).toBe(true);
    const privatePage = (await privateIdentityResponse.json()) as BookingConfigurationPage;
    const unknownPage = (await unknownIdentityResponse.json()) as BookingConfigurationPage;
    const publicResult = ({ docs, totalDocs, limit, page: currentPage, totalPages }: BookingConfigurationPage) => ({
      docs,
      totalDocs,
      limit,
      page: currentPage,
      totalPages,
    });
    expect(publicResult(privatePage)).toEqual(publicResult(unknownPage));
    expect(privatePage.docs).toEqual([]);
    expect(privatePage.totalDocs).toBe(0);

    const runtimeCatalogResponse = await apiContext.get(
      `/api/v2/instruments/fields/extraFields?search=${encodeURIComponent(fieldName)}`,
      { headers: { apiKey: appUser.apiKey } },
    );
    expect(runtimeCatalogResponse.ok(), await runtimeCatalogResponse.text()).toBe(true);
    const runtimeCatalog = (await runtimeCatalogResponse.json()) as RuntimeFieldCatalog;
    const runtimeDefinition = runtimeCatalog.fields.find(({ label }) => label === fieldName);
    expect(runtimeDefinition).toBeDefined();
    if (!runtimeDefinition) throw new Error(`Runtime field ${fieldName} was not published`);

    page.on("request", (request) => {
      if (new URL(request.url()).pathname === "/api/v2/booking-configurations") {
        collectionRequests.push(request.url());
      }
    });
    page.on("console", (message) => {
      if (message.type() === "error") browserErrors.push(message.text());
    });
    page.on("pageerror", (error) => browserErrors.push(error.message));

    await pageBookableItemsFilter.open();
    await pageBookableItemsFilter.openFilters();
    await pageBookableItemsFilter.addFilter();
    await pageBookableItemsFilter.chooseRuntimeField(1, fieldName);
    await pageBookableItemsFilter.chooseOperator(1, "equals");
    await pageBookableItemsFilter.scalarValue(1).fill(fieldValue);
    await page.keyboard.press("Escape");
    await pageBookableItemsFilter.applyFilters();

    await expect(pageBookableItemsFilter.row(matchingName)).toBeVisible();
    await expect(pageBookableItemsFilter.row(otherName)).toHaveCount(0);
    await expect(pageBookableItemsFilter.row(privateName)).toHaveCount(0);

    const runtimeRequest = collectionRequests.find((url) =>
      new URL(url).searchParams.get("where")?.includes(`target.extraFields.${runtimeDefinition.id}`),
    );
    expect(runtimeRequest).toBeDefined();
    if (!runtimeRequest) throw new Error("Runtime field filter request was not captured");
    expect(runtimeRequest).not.toContain(fieldValue);
    expect(runtimeRequest).toContain("%2F");
    const runtimeWhere = new URL(runtimeRequest).searchParams.get("where");
    expect(runtimeWhere).not.toBeNull();
    const runtimeExpression = parse(runtimeWhere ?? "");
    expect(runtimeExpression.type).toBe("COMPARISON");
    if (runtimeExpression.type === "COMPARISON") {
      expect(runtimeExpression.left.selector).toBe(`target.extraFields.${runtimeDefinition.id}`);
      expect(runtimeExpression.operator).toBe("==");
      expect(runtimeExpression.right.value).toBe(fieldValue);
    }

    await pageBookableItemsFilter.openFilters();
    await pageBookableItemsFilter.filterPanel.getByRole("button", { name: "Clear all" }).click();
    await pageBookableItemsFilter.openFilters();
    await pageBookableItemsFilter.addFilter();
    await pageBookableItemsFilter.chooseField(1, /Bookable item → Name/);
    await pageBookableItemsFilter.chooseOperator(1, "contains");
    await pageBookableItemsFilter.scalarValue(1).fill(matchingName);
    await page.keyboard.press("Escape");
    await pageBookableItemsFilter.applyFilters();
    await expect(pageBookableItemsFilter.row(matchingName)).toBeVisible();
    await expect(pageBookableItemsFilter.row(otherName)).toHaveCount(0);
    await expect(pageBookableItemsFilter.row(privateName)).toHaveCount(0);

    expect(browserErrors).toEqual([]);
  });
});
