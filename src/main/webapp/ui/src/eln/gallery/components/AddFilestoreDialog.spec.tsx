import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { AddFilestoreDialogStory } from "./AddFilestoreDialog.story";
import * as Jwt from "jsonwebtoken";
import AxeBuilder from "@axe-core/playwright";

const feature = test.extend<{
  Given: {
    "the add filestore dialog is mounted": () => Promise<void>;
  };
  When: {
    "the user selects a {filesystem}": ({
      filesystem,
    }: {
      filesystem: string;
    }) => Promise<void>;
    "the user selects a {folder}": ({
      folder,
    }: {
      folder: string;
    }) => Promise<void>;
  };
  Then: {
    "there shouldn't be any axe violations": () => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the add filestore dialog is mounted": async () => {
        await mount(<AddFilestoreDialogStory />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user selects a {filesystem}": async ({ filesystem }) => {
        await page.getByRole("radio", { name: filesystem }).click();
        await page.getByRole("button", { name: /Choose filesystem/i }).click();
      },
      "the user selects a {folder}": async ({ folder }) => {
        await page
          .getByRole("treeitem", { name: new RegExp(`^${folder}$`) })
          .locator("> div") // this is an accessibility issue that ought to be fixed in the component
          .click();
        await page.getByRole("button", { name: /Choose folder/i }).click();
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
             */
            return (
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region"
            );
          })
        ).toEqual([]);
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
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
  await router.route("/api/v1/gallery/filesystems", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        {
          id: 1,
          name: "irods test",
          url: "irods-test.researchspace.com",
          clientType: "IRODS",
          authType: "PASSWORD",
          options: {},
          loggedAs: null,
        },
      ]),
    });
  });
  await router.route(
    "/api/v1/gallery/filesystems/1/browse?remotePath=/",
    (route) => {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          remotePath: "/",
          filesystemInfo: {
            id: 1,
            name: "irods test",
            url: "irods-test.researchspace.com",
            clientType: "IRODS",
            authType: "PASSWORD",
            options: {},
            loggedAs: null,
          },
          loggedUser: "alice",
          showExtraDirs: false,
          showCurrentDir: false,
          content: [
            {
              name: "selenium",
              logicPath: "/tempZone/home/alice/selenium",
              fileSize: 0,
              nfsId: 10809,
              folder: true,
              modificationDate: "2024-07-02T08:16:38.000Z",
            },
            {
              name: "test",
              logicPath: "/tempZone/home/alice/test",
              fileSize: 0,
              nfsId: 10111,
              folder: true,
              modificationDate: "2024-12-16T01:32:36.000Z",
            },
            {
              name: "training_jpgs",
              logicPath: "/tempZone/home/alice/training_jpgs",
              fileSize: 0,
              nfsId: 10024,
              folder: true,
              modificationDate: "2024-05-17T06:30:18.000Z",
            },
            {
              name: "unit_test_DO_NOT_MANUAL_USE",
              logicPath: "/tempZone/home/alice/unit_test_DO_NOT_MANUAL_USE",
              fileSize: 0,
              nfsId: 10851,
              folder: true,
              modificationDate: "2024-12-16T01:18:08.000Z",
            },
          ],
          // },
        }),
      });
    }
  );
  await router.route(
    "/api/v1/gallery/filesystems/1/browse?remotePath=%2Ftest%2F",
    (route) => {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          remotePath: "/",
          filesystemInfo: {
            id: 1,
            name: "irods test",
            url: "irods-test.researchspace.com",
            clientType: "IRODS",
            authType: "PASSWORD",
            options: {},
            loggedAs: null,
          },
          loggedUser: "alice",
          showExtraDirs: false,
          showCurrentDir: false,
          content: [],
        }),
      });
    }
  );
});

feature.afterEach(({}) => {});

test.describe("AddFilestoreDialog", () => {
  feature("Should have no axe violations", async ({ Given, When, Then }) => {
    await Given["the add filestore dialog is mounted"]();
    await Then["there shouldn't be any axe violations"]();
    await When["the user selects a {filesystem}"]({ filesystem: "irods test" });
    await Then["there shouldn't be any axe violations"]();
    await When["the user selects a {folder}"]({
      folder: "test",
    });
    await Then["there shouldn't be any axe violations"]();
  });
});
