import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { UsersPage } from "./index";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import PDF_CONFIG from "./__tests__/pdfConfig.json";
import USER_LISTING from "./__tests__/userListing.json";
import fs from "fs/promises";

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
