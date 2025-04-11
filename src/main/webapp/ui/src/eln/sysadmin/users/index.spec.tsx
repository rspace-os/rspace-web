import { test, expect } from "@playwright/experimental-ct-react";
import { Download } from "playwright-core";
import React from "react";
import { UsersPage } from "./index";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import PDF_CONFIG from "./__tests__/pdfConfig.json";
import USER_LISTING from "./__tests__/userListing.json";
import fs from "fs/promises";
import AxeBuilder from "@axe-core/playwright";

const feature = test.extend<{
  Given: {
    "the sysadmin is on the users page": () => Promise<void>;
  };
  When: {
    "one row is selected": () => Promise<void>;
    "a CSV export is downloaded": () => Promise<Download>;
    "a CSV export of the selected rows is downloaded": () => Promise<Download>;
  };
  Then: {
    "it should have a precise usage column": (csv: Download) => Promise<void>;
    "it should have the same number of columns as are available to view, except for 'Full Name'": (
      csv: Download
    ) => Promise<void>;
    "it should have {count} rows": (
      csv: Download,
      { count }: { count: number }
    ) => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the sysadmin is on the users page": async () => {
        await mount(
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
              <UsersPage />
            </ThemeProvider>
          </StyledEngineProvider>
        );
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "one row is selected": async (): Promise<void> => {
        const checkboxes = page.getByRole("checkbox", {
          name: "Select row",
        });
        await expect(checkboxes).toHaveCount(10);
        await checkboxes.first().click();
      },
      "a CSV export is downloaded": async (): Promise<Download> => {
        await page.getByRole("button", { name: /Export/ }).click();
        const [download] = await Promise.all([
          page.waitForEvent("download"),
          page
            .getByRole("menuitem", {
              name: /Export this page of rows to CSV/,
            })
            .click(),
        ]);
        return download;
      },
      "a CSV export of the selected rows is downloaded":
        async (): Promise<Download> => {
          await page.getByRole("button", { name: /Export/ }).click();
          const [download] = await Promise.all([
            page.waitForEvent("download"),
            page
              .getByRole("menuitem", {
                name: /Export selected rows to CSV/,
              })
              .click(),
          ]);
          return download;
        },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "it should have a precise usage column": async (csv: Download) => {
        const path = await csv.path();
        const fileContents = await fs.readFile(path, "utf8");
        expect(fileContents).toContain("362006");
        expect(fileContents).not.toContain("362.01 kB");
      },
      "it should have the same number of columns as are available to view, except for 'Full Name'":
        async (csv: Download) => {
          await page.getByRole("button", { name: /Select columns/ }).click();
          const numberOfColumns = await page
            .getByRole("checkbox", {
              name: /^(?!Select all rows$|Select row$|Checkbox selection$|Show\/Hide All$|Full Name$).*$/,
            })
            .count();
          const path = await csv.path();
          const fileContents = await fs.readFile(path, "utf8");
          const lines = fileContents.split("\n");
          const header = lines[0].split(",");
          expect(header.length).toBe(numberOfColumns);
        },
      "it should have {count} rows": async (
        csv: Download,
        { count }: { count: number }
      ) => {
        const path = await csv.path();
        const fileContents = await fs.readFile(path, "utf8");
        const lines = fileContents.split("\n");
        expect(lines.length).toBe(count + 1);
      },
    });
  },
});

test.beforeEach(async ({ page, router }) => {
  await page.evaluate(() => {
    // @ts-expect-error global
    window.RS = {
      newFileStoresExportEnabled: true,
    };
  });

  await router.route("/export/ajax/defaultPDFConfig", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(PDF_CONFIG),
    });
  });

  await router.route(/system\/ajax\/jsonList.*/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(USER_LISTING),
    });
  });
});

test.describe("Grant User PI role", () => {
  test("When `checkVerificationPasswordNeeded` returns true, a message should be shown.", async ({
    mount,
    page,
    router,
  }) => {
    await router.route(
      "/vfpwd/ajax/checkVerificationPasswordNeeded",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ data: true }),
        });
      }
    );

    await mount(
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <UsersPage />
        </ThemeProvider>
      </StyledEngineProvider>
    );

    const row = page.getByRole("row", { name: /user8h/ });
    const checkbox = row.getByRole("checkbox");
    await checkbox.click();

    await page.getByRole("button", { name: /Actions/ }).click();
    await page.getByRole("menuitem", { name: /Grant PI role/ }).click();

    await expect(
      page.getByText(
        "Please set your verification password in My RSpace before performing this action."
      )
    ).toBeVisible();
  });

  test("When `checkVerificationPasswordNeeded` returns false, the message should not be shown.", async ({
    mount,
    page,
    router,
  }) => {
    await router.route(
      "/vfpwd/ajax/checkVerificationPasswordNeeded",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ data: false }),
        });
      }
    );

    await mount(
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <UsersPage />
        </ThemeProvider>
      </StyledEngineProvider>
    );

    const row = page.getByRole("row", { name: /user8h/ });
    const checkbox = row.getByRole("checkbox");
    await checkbox.click();

    await page.getByRole("button", { name: /Actions/ }).click();
    await page.getByRole("menuitem", { name: /Grant PI role/ }).click();

    await expect(
      page.getByText(
        "Please set your verification password in My RSpace before performing this action."
      )
    ).not.toBeVisible();
  });
});

test.describe("Accessibility", () => {
  test("Should have no axe violations.", async ({ mount, page }) => {
    await mount(
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <UsersPage />
        </ThemeProvider>
      </StyledEngineProvider>
    );
    
    // Wait for the table to be loaded
    await expect(page.getByRole("table")).toBeVisible();
    
    // Run the accessibility scan
    const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
    
    // Filter out known issues that can't be fixed in component tests
    expect(
      accessibilityScanResults.violations.filter((v) => {
        /*
         * These violations are expected in component tests as we're not rendering
         * a complete page with proper document structure:
         * 
         * 1. MUI DataGrid renders its immediate children with role=presentation,
         *    which Firefox considers to be a violation
         * 2. Component tests don't have main landmarks as they're isolated components
         * 3. Component tests typically don't have h1 headings as they're not full pages
         * 4. Content not in landmarks is expected in component testing context
         */
        return (
          v.description !== "Ensure elements with an ARIA role that require child roles contain them" &&
          v.id !== "landmark-one-main" &&
          v.id !== "page-has-heading-one" &&
          v.id !== "region"
        );
      })
    ).toEqual([]);
  });
});

test.describe("CSV Export", () => {
  test.describe("Selection", () => {
    feature(
      "When no rows are selected, every row of the current page should be included in the export",
      async ({ Given, When, Then }) => {
        await Given["the sysadmin is on the users page"]();
        // Note that no selection is made
        const download = await When["a CSV export is downloaded"]();
        await Then["it should have {count} rows"](download, { count: 10 });
      }
    );
    feature(
      "When one row is selected, just it should be included in the export",
      async ({ Given, When, Then }) => {
        await Given["the sysadmin is on the users page"]();
        await When["one row is selected"]();
        const download = await When[
          "a CSV export of the selected rows is downloaded"
        ]();
        await Then["it should have {count} rows"](download, { count: 1 });
      }
    );
  });
  test.describe("Columns", () => {
    feature(
      "All of the columns should be included in the CSV file.",
      async ({ Given, When, Then }) => {
        await Given["the sysadmin is on the users page"]();
        const download = await When["a CSV export is downloaded"]();
        await Then[
          "it should have the same number of columns as are available to view, except for 'Full Name'"
        ](download);
        /*
         * Full Name is not included in the CSV file because it is a derived
         * column from the first and last name columns. It is provided as a
         * convenience in the UI but would be redundant if included in the CSV.
         */
      }
    );
    feature(
      "The usage column should be a precise number.",
      async ({ Given, When, Then }) => {
        await Given["the sysadmin is on the users page"]();
        const download = await When["a CSV export is downloaded"]();
        await Then["it should have a precise usage column"](download);
        /*
         * Because the CSV file can then be imported into a spreadsheet program
         * and the usage column will be formatted as a number, for sorting and
         * other purposes.
         */
      }
    );
  });
});
