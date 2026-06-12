import fs from "node:fs/promises";
import { expect, test } from "@playwright/experimental-ct-react";
import * as Jwt from "jsonwebtoken";
import type { Download } from "playwright-core";
import identifiersJson from "../../__tests__/identifiers.json";
import { SimpleIgsnTable } from "./IgsnTable.story";

/*
 * Only the CSV-export cases that assert the CONTENTS of a REAL downloaded file
 * remain here. Everything else has been converted to a Vitest jsdom unit test
 * in IgsnTable.test.tsx. Under Vitest the @mui/x-data-grid stub's
 * `exportDataAsCsv` builds a CSV string and calls `window.URL.createObjectURL`
 * but never writes a file, so these two cases must stay in Playwright where the
 * browser actually triggers a `download` event and writes the file to disk.
 */

const feature = test.extend<{
  Given: {
    "the researcher is viewing the IGSN table": () => Promise<void>;
  };
  Once: {
    "the table has loaded": () => Promise<void>;
  };
  When: {
    "a CSV export is downloaded": () => Promise<Download>;
    "the researcher selects {count} IGSNs": ({ count }: { count: number }) => Promise<void>;
  };
  Then: {
    "{CSV} should have {count} rows": ({ csv, count }: { csv: Download; count: number }) => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the researcher is viewing the IGSN table": async () => {
        await mount(<SimpleIgsnTable />);
      },
    });
  },
  Once: async ({ page }, use) => {
    await use({
      "the table has loaded": async () => {
        await page.waitForFunction(() => {
          const rows = document.querySelectorAll('[role="row"]').length;
          const noIgsnMessage = document.body.textContent?.includes("No IGSN IDs");
          return rows > 1 || noIgsnMessage; // (1 is for the header row) or empty state message
        });
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "a CSV export is downloaded": async () => {
        await page.getByRole("button", { name: /Export/ }).click();
        const [download] = await Promise.all([
          page.waitForEvent("download"),
          page
            .getByRole("menuitem", {
              name: /Export to CSV/,
            })
            .click(),
        ]);
        return download;
      },
      "the researcher selects {count} IGSNs": async ({ count }: { count: number }) => {
        for (let i = 0; i < count; i++) {
          await page
            .getByRole("checkbox", { name: /Select row/ })
            .nth(0) // for some reason, only the unchecked ones are found
            .click();
        }
      },
    });
  },
  // biome-ignore lint/correctness/noEmptyPattern: Playwright fixture takes no destructured deps
  Then: async ({}, use) => {
    await use({
      "{CSV} should have {count} rows": async ({ csv, count }) => {
        const path = await csv.path();
        const fileContents = await fs.readFile(path, "utf8");
        const lines = fileContents.split("\n");
        expect(lines.length).toBe(count + 1);
      },
    });
  },
  // biome-ignore lint/correctness/noEmptyPattern: Playwright fixture takes no destructured deps
  networkRequests: async ({}, use) => {
    await use([]);
  },
});
feature.beforeEach(async ({ router, page, networkRequests }) => {
  await router.route("/userform/ajax/inventoryOauthToken", (route) => {
    const payload = {
      iss: "http://localhost:8080",
      iat: Date.now(),
      exp: Math.floor(Date.now() / 1000) + 300,
      refreshTokenHash: "fe15fa3d5e3d5a47e33e9e34229b1ea2314ad6e6f13fa42addca4f1439582a4d",
    };
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: Jwt.sign(payload, "dummySecretKey"),
      }),
    });
  });
  await router.route(
    (url) => url.pathname === "/api/inventory/v1/identifiers",
    (route) => {
      const url = new URL(route.request().url());
      const state = url.searchParams.get("state");
      const isAssociated = url.searchParams.get("isAssociated");

      const searchTerm = url.searchParams.get("searchTerm");

      let filteredIdentifiers = identifiersJson;
      if (state) {
        filteredIdentifiers = filteredIdentifiers.filter((identifier) => identifier.state === state);
      }
      if (searchTerm) {
        filteredIdentifiers = filteredIdentifiers.filter((identifier) => identifier.doi.includes(searchTerm));
      }
      if (isAssociated === "true") {
        filteredIdentifiers = filteredIdentifiers.filter((identifier) => identifier.associatedGlobalId !== null);
      } else if (isAssociated === "false") {
        filteredIdentifiers = filteredIdentifiers.filter((identifier) => identifier.associatedGlobalId === null);
      }
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(filteredIdentifiers),
      });
    },
  );
  page.on("request", (request) => {
    networkRequests.push(new URL(request.url()));
  });
});
feature.afterEach(({ networkRequests }) => {
  networkRequests.splice(0, networkRequests.length);
});
test.describe("IGSN Table", () => {
  feature("When there is no selection, all rows should be included in the export.", async ({ Given, When, Then }) => {
    await Given["the researcher is viewing the IGSN table"]();
    // Note that no selection is made
    const csv = await When["a CSV export is downloaded"]();
    await Then["{CSV} should have {count} rows"]({ csv, count: 4 });
  });
  feature(
    "When some IGSNs are selected, CSV exports should include just those rows",
    async ({ Given, Once, When, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await Once["the table has loaded"]();
      await When["the researcher selects {count} IGSNs"]({ count: 2 });
      const csv = await When["a CSV export is downloaded"]();
      await Then["{CSV} should have {count} rows"]({ csv, count: 2 });
    },
  );
});
