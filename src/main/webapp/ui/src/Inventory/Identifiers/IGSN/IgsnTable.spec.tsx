import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import identifiersJson from "../../__tests__/identifiers.json";
import IgsnTableStory from "./IgsnTable.story";

const feature = test.extend<{
  Given: {
    "the researcher is viewing the IGSN table": () => Promise<void>;
  };
  Once: {
    "the table has loaded": () => Promise<void>;
  };
  When: {};
  Then: {
    "a table should be shown": () => Promise<void>;
    "the default columns should be Select, DOI, State, and Linked Item": () => Promise<void>;
    "there should be four rows": () => Promise<void>;
  };
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
  When: async ({}, use) => {
    await use({});
  },
  Then: async ({ page }, use) => {
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
    });
  },
});

test.beforeEach(async ({ router }) => {
  await router.route("/userform/ajax/inventoryOauthToken", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3MzQzNDI5NTYsImV4cCI6MTczNDM0NjU1NiwicmVmcmVzaFRva2VuSGFzaCI6ImZlMTVmYTNkNWUzZDVhNDdlMzNlOWUzNDIyOWIxZWEyMzE0YWQ2ZTZmMTNmYTQyYWRkY2E0ZjE0Mzk1ODJhNGQifQ.HCKre3g_P1wmGrrrnQncvFeT9pAePFSc4UPuyP5oehI",
      }),
    });
  });

  await router.route("/api/inventory/v1/identifiers", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(identifiersJson),
    });
  });
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
});
