import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import {
  ClosedFieldmarkImportDialog,
  OpenFieldmarkImportDialog,
} from "./FieldmarkImportDialog.story";
import AxeBuilder from "@axe-core/playwright";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "the fieldmark import dialog is rendered but closed": () => Promise<void>;
    "the fieldmark import dialog is open": () => Promise<void>;
  };
  When: {
    "the dialog is in the DOM": () => Promise<void>;
  };
  Then: {
    "there shouldn't be any axe violations": () => Promise<void>;
  };
}>({
  Given: async ({ mount, page }, use) => {
    await use({
      "the fieldmark import dialog is rendered but closed": async () => {
        await mount(<ClosedFieldmarkImportDialog />);
      },
      "the fieldmark import dialog is open": async () => {
        await mount(<OpenFieldmarkImportDialog />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the dialog is in the DOM": async () => {
        const dialog = page.getByRole("dialog", { includeHidden: true });
        await expect(dialog).toBeHidden();
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "there shouldn't be any axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(
          accessibilityScanResults.violations.filter((v) => {
            /*
             * These violations are expected in component tests as we're not rendering
             * a complete page with proper document structure:
             *
             * 1. Component tests don't have main landmarks as they're isolated components
             * 2. Component tests typically don't have h1 headings as they're not full pages
             * 3. Content not in landmarks is expected in component testing context
             * 4. DataGrids are not supposed to have ProgressBars, but this is a MUI issue
             */
            return (
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region" &&
              !(
                v.id === "aria-required-children" &&
                v.nodes[0]?.target[0] === ".MuiDataGrid-main" &&
                v.nodes[0]?.any[0].data.values === "[role=progressbar]"
              )
            );
          }),
        ).toEqual([]);
      },
    });
  },
});

feature.beforeEach(async ({ router }) => {
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
  await router.route("/api/v1/userDetails/uiNavigationData", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        bannerImgSrc: "/public/banner",
        visibleTabs: {
          inventory: true,
          myLabGroups: true,
          published: true,
          system: true,
        },
        userDetails: {
          username: "user1a",
          fullName: "user user",
          email: "user@user.com",
          orcidId: null,
          orcidAvailable: false,
          profileImgSrc: null,
          lastSession: "2025-03-25T15:45:57.000Z",
        },
        operatedAs: false,
        nextMaintenance: null,
      }),
    });
  });
});

test.describe("FieldmarkImportDialog", () => {
  test.describe("accessibility", () => {
    feature(
      "should not have any accessibility issues when closed",
      async ({ Given, When, Then }) => {
        await Given["the fieldmark import dialog is rendered but closed"]();
        await Then["there shouldn't be any axe violations"]();
      },
    );
    feature(
      "should not have any accessibility issues when open",
      async ({ Given, When, Then }) => {
        await Given["the fieldmark import dialog is open"]();
        await Then["there shouldn't be any axe violations"]();
      },
    );
  });
});
