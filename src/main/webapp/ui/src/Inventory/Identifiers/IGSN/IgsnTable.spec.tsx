import { test, expect } from "@playwright/experimental-ct-react";
import { Download } from "playwright-core";
import React from "react";
import identifiersJson from "../../__tests__/identifiers.json";
import IgsnTableStory from "./IgsnTable.story";
import fs from "fs/promises";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "the researcher is viewing the IGSN table": () => Promise<void>;
  };
  Once: {
    "the table has loaded": () => Promise<void>;
  };
  When: {
    "a CSV export is downloaded": () => Promise<Download>;
    "the researcher selects 'Draft' from the state menu": () => Promise<void>;
    "the researcher selects 'No Linked Item' from the Linked Item menu": () => Promise<void>;
    "the researcher selects the IGSN with DOI '10.82316/khma-em96'": () => Promise<void>;
    "the researcher selects {count} IGSNs": ({
      count,
    }: {
      count: number;
    }) => Promise<void>;
  };
  Then: {
    "a table should be shown": () => Promise<void>;
    "the default columns should be Select, DOI, State, and Linked Item": () => Promise<void>;
    "there should be four rows": () => Promise<void>;
    "there should be a menu for changing column visibility": () => Promise<void>;
    "there should be a menu for exporting the IGSN table to CSV": () => Promise<void>;
    "{CSV} should have {count} rows": ({
      csv,
      count,
    }: {
      csv: Download;
      count: number;
    }) => Promise<void>;
    "there should be a network request with state set to 'draft'": () => void;
    "there should be a network request with isAssociated set to 'false'": () => void;
    "the IGSN with DOI '10.82316/khma-em96' is added to the selection state": () => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the researcher is viewing the IGSN table": async () => {
        await mount(<IgsnTableStory />);
      },
    });
  },
  Once: async ({ page }, use) => {
    await use({
      "the table has loaded": async () => {
        await page.waitForFunction(() => {
          const rows = document.querySelectorAll('[role="row"]').length;
          return rows > 1; // (1 is for the header row)
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
      "the researcher selects 'Draft' from the state menu": async () => {
        await page.getByRole("button", { name: /State/ }).click();
        await page.getByRole("menuitem", { name: /Draft/ }).click();
      },
      "the researcher selects 'No Linked Item' from the Linked Item menu":
        async () => {
          await page.getByRole("button", { name: /Linked Item/ }).click();
          await page.getByRole("menuitem", { name: /No Linked Item/ }).click();
        },
      "the researcher selects the IGSN with DOI '10.82316/khma-em96'":
        async () => {
          const row = page
            .getByRole("row", { name: /10.82316\/khma-em96/ })
            .first();
          await row.getByRole("checkbox").first().click();
        },
      "the researcher selects {count} IGSNs": async ({
        count,
      }: {
        count: number;
      }) => {
        for (let i = 0; i < count; i++) {
          await page
            .getByRole("checkbox", { name: /Select row/ })
            .nth(0) // for some reason, only the unchecked ones are found
            .click();
        }
      },
    });
  },
  Then: async ({ page, networkRequests }, use) => {
    await use({
      "a table should be shown": async () => {
        const table = page.getByRole("grid");
        await expect(table).toBeVisible();
      },
      "the default columns should be Select, DOI, State, and Linked Item":
        async () => {
          const headers = await page
            .getByRole("columnheader")
            .allTextContents();
          expect(headers).toEqual(["", "DOI", "State", "Linked Item"]);
          // the empty string at the beginning is the checkbox column
        },
      "there should be four rows": async () => {
        const rows = await page.getByRole("row").count();
        expect(rows).toBe(5); // + 1 for the header row
      },
      "there should be a menu for changing column visibility": async () => {
        const menuButton = page.getByRole("button", { name: "Columns" });
        await menuButton.click();
        const menu = page.getByRole("tooltip");
        await expect(menu).toBeVisible();
      },
      "there should be a menu for exporting the IGSN table to CSV":
        async () => {
          const menuButton = page.getByRole("button", { name: "Export" });
          await menuButton.click();
          const menu = page.getByRole("tooltip");
          await expect(menu).toBeVisible();
        },
      "{CSV} should have {count} rows": async ({ csv, count }) => {
        const path = await csv.path();
        const fileContents = await fs.readFile(path, "utf8");
        const lines = fileContents.split("\n");
        expect(lines.length).toBe(count + 1);
      },
      "there should be a network request with state set to 'draft'": () => {
        expect(
          networkRequests
            .find((url) => url.searchParams.has("state"))
            ?.searchParams.get("state")
        ).toBe("draft");
      },
      "there should be a network request with isAssociated set to 'false'":
        () => {
          expect(
            networkRequests
              .find((url) => url.searchParams.has("isAssociated"))
              ?.searchParams.get("isAssociated")
          ).toBe("false");
        },
      "the IGSN with DOI '10.82316/khma-em96' is added to the selection state":
        async () => {
          /*
           * We can't check that setSelectedIdentifiers has actually been called
           * using Playwright, but because IgsnTable.story renders the selection
           * we can check what's been rendered.
           */
          await expect(
            page.getByLabel("selected IGSNs").getByText("10.82316/khma-em96")
          ).toBeVisible();
        },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router, page, networkRequests }) => {
  await router.route("/userform/ajax/inventoryOauthToken", (route) => {
    const payload = {
      iss: "http://localhost:8080",
      iat: new Date().getTime(),
      exp: Math.floor(Date.now() / 1000) + 300,
      refreshTokenHash:
        "fe15fa3d5e3d5a47e33e9e34229b1ea2314ad6e6f13fa42addca4f1439582a4d",
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
      const filteredIdentifiers = state
        ? identifiersJson.filter((identifier) => identifier.state === state)
        : identifiersJson;
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(filteredIdentifiers),
      });
    }
  );

  page.on("request", (request) => {
    networkRequests.push(new URL(request.url()));
  });
});

feature.afterEach(({ networkRequests }) => {
  networkRequests.splice(0, networkRequests.length);
});

test.describe("IGSN Table", () => {
  feature(
    "When the researcher is viewing the IGSN table, a table should be shown.",
    async ({ Given, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await Then["a table should be shown"]();
    }
  );

  feature(
    "The default columns should be Select, DOI, State, and Linked Item",
    async ({ Given, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await Then[
        "the default columns should be Select, DOI, State, and Linked Item"
      ]();
    }
  );

  feature(
    "The mocked data displays four rows",
    async ({ Given, Once, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await Once["the table has loaded"]();
      await Then["there should be four rows"]();
    }
  );

  feature(
    "There should be a menu for changing column visibility",
    async ({ Given, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await Then["there should be a menu for changing column visibility"]();
    }
  );

  feature(
    "There should be a menu for exporting the IGSN table to CSV",
    async ({ Given, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await Then[
        "there should be a menu for exporting the IGSN table to CSV"
      ]();
    }
  );

  feature(
    "When there is no selection, all rows should be included in the export.",
    async ({ Given, When, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      // Note that no selection is made
      const csv = await When["a CSV export is downloaded"]();
      await Then["{CSV} should have {count} rows"]({ csv, count: 4 });
    }
  );

  feature(
    "Filtering by state makes API call with state parameter",
    async ({ Given, When, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await When["the researcher selects 'Draft' from the state menu"]();
      void Then[
        "there should be a network request with state set to 'draft'"
      ]();
    }
  );

  feature(
    "Filtering by linked item makes API call with isAssociated parameter",
    async ({ Given, When, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await When[
        "the researcher selects 'No Linked Item' from the Linked Item menu"
      ]();
      void Then[
        "there should be a network request with isAssociated set to 'false'"
      ]();
    }
  );

  feature(
    "When a researcher selects an identifier, the selection state is updated",
    async ({ Given, Once, When, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await Once["the table has loaded"]();
      await When[
        "the researcher selects the IGSN with DOI '10.82316/khma-em96'"
      ]();
      await Then[
        "the IGSN with DOI '10.82316/khma-em96' is added to the selection state"
      ]();
    }
  );

  feature(
    "When some IGSNs are selected, CSV exports should include just those rows",
    async ({ Given, Once, When, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await Once["the table has loaded"]();
      await When["the researcher selects {count} IGSNs"]({ count: 2 });
      const csv = await When["a CSV export is downloaded"]();
      await Then["{CSV} should have {count} rows"]({ csv, count: 2 });
    }
  );
});
