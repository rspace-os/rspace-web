import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import IgsnTable from "./IgsnTable";

const feature = test.extend<{
  Given: {
    "the researcher is viewing the IGSN table": () => Promise<void>;
  };
  When: {};
  Then: {
    "a table should be shown": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the researcher is viewing the IGSN table": async () => {
        await mount(
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
              <IgsnTable />
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
    });
  },
});

test.beforeEach(async ({ page, router }) => {});

test.describe("IGSN Table", () => {
  feature(
    "When the researcher is viewing the IGSN table, a table should be shown.",
    async ({ Given, Then }) => {
      await Given["the researcher is viewing the IGSN table"]();
      await Then["a table should be shown"]();
    }
  );
});
