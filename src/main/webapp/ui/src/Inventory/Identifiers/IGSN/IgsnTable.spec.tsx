import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import IgsnTable from "./IgsnTable";
import RsSet from "../../../util/set";

const feature = test.extend<{
  Given: {
    "the researcher is viewing the IGSN table": () => Promise<void>;
  };
  When: {};
  Then: {
    "a table should be shown": () => Promise<void>;
    "the default columns should be Select, DOI, State, and Linked Item": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the researcher is viewing the IGSN table": async () => {
        await mount(
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
              <IgsnTable selectedIgsns={[]} setSelectedIgsns={() => {}} />
            </ThemeProvider>
          </StyledEngineProvider>
        );
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
      body: JSON.stringify({
        data: [],
      }),
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
});
