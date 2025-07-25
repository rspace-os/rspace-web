import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { FieldmarkImportDialogStory } from "./FieldmarkImportDialog.story";
import AxeBuilder from "@axe-core/playwright";
import * as Jwt from "jsonwebtoken";
import { sleep } from "@/util/Util";

const feature = test.extend<{
  Given: {
    "the fieldmark import dialog is mounted": () => Promise<void>;
  };
  When: {
    "the dialog is in the DOM": () => Promise<void>;
    "the notebooks have been fetched": () => Promise<void>;
    "the user selects a notebook and clicks import": () => Promise<void>;
    "the user selects a notebook with no identifier columns and clicks import": () => Promise<void>;
    "the user selects a notebook with identifier columns, selects an identifier column, and clicks import": () => Promise<void>;
    "the user selects a notebook and clicks import for alert testing": () => Promise<void>;
    "the user selects a notebook that will trigger IGSN error": () => Promise<void>;
  };
  Then: {
    "there shouldn't be any axe violations": () => Promise<void>;
    "the notebooks should be displayed in the table": () => Promise<void>;
    "an import request should be made to the server with the correct notebook ID": () => void;
    "an import request should be made without an identifier column": () => void;
    "an import request should be made with the selected identifier column": () => void;
    "an importing alert should be visible": () => Promise<void>;
    "a success alert should be visible": () => Promise<void>;
    "the import button should show loading state": () => Promise<void>;
    "the IGSN message should be displayed": () => Promise<void>;
  };
  networkRequests: Array<{ url: URL; postData: string | null }>;
}>({
  Given: async ({ mount, page }, use) => {
    await use({
      "the fieldmark import dialog is mounted": async () => {
        await mount(<FieldmarkImportDialogStory />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the dialog is in the DOM": async () => {
        const dialog = page.getByRole("dialog", { includeHidden: true });
        await expect(dialog).toBeHidden();
      },
      "the notebooks have been fetched": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();

        // Wait for the DataGrid to appear
        const dataGrid = page.getByRole("grid");
        await expect(dataGrid).toBeVisible();
      },
      "the user selects a notebook and clicks import": async () => {
        await page
          .getByRole("radio", { name: "Select notebook: Test Notebook 1" })
          .click();
        await page.getByRole("button", { name: "Import" }).click();
      },
      "the user selects a notebook with no identifier columns and clicks import":
        async () => {
          await page
            .getByRole("radio", {
              name: "Select notebook: Notebook No Identifiers",
            })
            .click();
          await page.getByRole("button", { name: "Import" }).click();
        },
      "the user selects a notebook with identifier columns, selects an identifier column, and clicks import":
        async () => {
          await page
            .getByRole("radio", {
              name: "Select notebook: Notebook With Identifiers",
            })
            .click();
          const identifierSelect = page.getByRole("combobox", {
            name: "Identifier field",
          });
          await identifierSelect.click();
          await page.getByRole("option", { name: "sample_id" }).click();
          await page.getByRole("button", { name: "Import" }).click();
        },
      "the user selects a notebook and clicks import for alert testing":
        async () => {
          await page
            .getByRole("radio", { name: "Select notebook: Test Notebook 1" })
            .click();
          await page.getByRole("button", { name: "Import" }).click();
        },
      "the user selects a notebook that will trigger IGSN error": async () => {
        await page
          .getByRole("radio", {
            name: "Select notebook: Notebook IGSN Error",
          })
          .click();
      },
    });
  },
  Then: async ({ page, networkRequests }, use) => {
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
                (v.nodes[0]?.any[0]?.data?.values === "[role=progressbar]" ||
                  v.nodes[0]?.any[0]?.data?.values === "[role=presentation]")
              )
            );
          }),
        ).toEqual([]);
      },
      "the notebooks should be displayed in the table": async () => {
        await expect(
          page.getByRole("gridcell", { name: /^Test Notebook 1$/ }),
        ).toBeVisible();
        await expect(
          page.getByRole("gridcell", { name: /^Test Notebook 2$/ }),
        ).toBeVisible();
        await expect(
          page.getByRole("gridcell", { name: /^Notebook No Identifiers$/ }),
        ).toBeVisible();
        await expect(
          page.getByRole("gridcell", { name: /^Notebook With Identifiers$/ }),
        ).toBeVisible();
        await expect(
          page.getByRole("gridcell", { name: "draft" }).first(),
        ).toBeVisible();
        await expect(
          page.getByRole("gridcell", { name: "published" }),
        ).toBeVisible();

        await expect(
          page.getByRole("radio", { name: "Select notebook: Test Notebook 1" }),
        ).toBeVisible();
        await expect(
          page.getByRole("radio", { name: "Select notebook: Test Notebook 2" }),
        ).toBeVisible();
        await expect(
          page.getByRole("radio", {
            name: "Select notebook: Notebook No Identifiers",
          }),
        ).toBeVisible();
        await expect(
          page.getByRole("radio", {
            name: "Select notebook: Notebook With Identifiers",
          }),
        ).toBeVisible();
      },
      "an import request should be made to the server with the correct notebook ID":
        () => {
          const importRequest = networkRequests.find(
            (request) =>
              request.url.pathname ===
                "/api/inventory/v1/import/fieldmark/notebook" &&
              request.postData?.includes('"notebookId":"test-project-1"'),
          );
          expect(importRequest).toBeDefined();
        },
      "an import request should be made without an identifier column": () => {
        const importRequest = networkRequests.find(
          (request) =>
            request.url.pathname ===
              "/api/inventory/v1/import/fieldmark/notebook" &&
            request.postData?.includes(
              '"notebookId":"test-project-no-identifiers"',
            ) &&
            !request.postData?.includes('"identifierColumn"'),
        );
        expect(importRequest).toBeDefined();
      },
      "an import request should be made with the selected identifier column":
        () => {
          const importRequest = networkRequests.find(
            (request) =>
              request.url.pathname ===
                "/api/inventory/v1/import/fieldmark/notebook" &&
              request.postData?.includes(
                '"notebookId":"test-project-with-identifiers"',
              ) &&
              request.postData?.includes('"identifier":"sample_id"'),
          );
          expect(importRequest).toBeDefined();
        },
      "an importing alert should be visible": async () => {
        const alert = page.getByRole("alert");
        await expect(alert).toBeVisible();
        await expect(alert).toContainText("Importing notebook");
        await expect(alert).toContainText("Test Notebook 1");
      },
      "a success alert should be visible": async () => {
        const alert = page.getByRole("alert");
        await expect(alert).toBeVisible();
        await expect(alert).toContainText("Successfully imported notebook");
        await alert
          .getByRole("button", { name: "1 sub-messages. Toggle to show" })
          .click();
        const subMessage = alert
          .getByRole("alert")
          .filter({ hasText: "Test Container from Test Notebook 1" });
        const link = subMessage.getByRole("link", { name: "IC123456" });
        await expect(link).toBeVisible();
        expect(link).toHaveAttribute("href", "/globalId/IC123456");
      },
      "the import button should show loading state": async () => {
        const importButton = page.getByRole("button", { name: "Import" });
        await expect(importButton).toBeDisabled();
      },
      "the IGSN message should be displayed": async () => {
        await expect(
          page.getByText(
            "RSpace can link pre-registered IGSN IDs with samples imported by Fieldmark.",
          ),
        ).toBeVisible();
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router, page, networkRequests }) => {
  page.on("request", (request) => {
    networkRequests.push({
      url: new URL(request.url()),
      postData: request.postData(),
    });
  });
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
  await router.route("/api/v1/userDetails/whoami", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        username: "user1a",
        fullName: "Test User",
        email: "test@example.com",
      }),
    });
  });
  await router.route("/api/inventory/v1/fieldmark/notebooks", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        {
          name: "Test Notebook 1",
          metadata: {
            project_id: "test-project-1",
            ispublic: false,
            pre_description: "A test notebook for development",
            project_lead: "Test User 1",
          },
          status: "draft",
        },
        {
          name: "Test Notebook 2",
          metadata: {
            project_id: "test-project-2",
            ispublic: true,
            pre_description: "Another test notebook",
            project_lead: "Test User 2",
          },
          status: "published",
        },
        {
          name: "Notebook No Identifiers",
          metadata: {
            project_id: "test-project-no-identifiers",
            ispublic: false,
            pre_description: "A notebook with no identifier columns",
            project_lead: "Test User 3",
          },
          status: "draft",
        },
        {
          name: "Notebook With Identifiers",
          metadata: {
            project_id: "test-project-with-identifiers",
            ispublic: false,
            pre_description: "A notebook with identifier columns",
            project_lead: "Test User 4",
          },
          status: "draft",
        },
        {
          name: "Notebook IGSN Error",
          metadata: {
            project_id: "test-project-igsn-error",
            ispublic: false,
            pre_description: "A notebook that triggers IGSN error",
            project_lead: "Test User 5",
          },
          status: "draft",
        },
      ]),
    });
  });
  await router.route(
    "/api/inventory/v1/import/fieldmark/notebook",
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          containerName: "Test Container from Test Notebook 1",
          containerGlobalId: "IC123456",
        }),
      });
    },
  );
  await router.route(
    "/api/inventory/v1/fieldmark/notebooks/igsnCandidateFields?notebookId=test-project-1",
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(["sample_id", "batch_id"]),
      });
    },
  );
  await router.route(
    "/api/inventory/v1/fieldmark/notebooks/igsnCandidateFields?notebookId=test-project-2",
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([]),
      });
    },
  );
  await router.route(
    "/api/inventory/v1/fieldmark/notebooks/igsnCandidateFields?notebookId=test-project-no-identifiers",
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([]),
      });
    },
  );
  await router.route(
    "/api/inventory/v1/fieldmark/notebooks/igsnCandidateFields?notebookId=test-project-with-identifiers",
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(["sample_id", "batch_id"]),
      });
    },
  );
  await router.route(
    "/api/inventory/v1/fieldmark/notebooks/igsnCandidateFields?notebookId=test-project-igsn-error",
    async (route) => {
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            validationErrors: [
              {
                message: "IGSN integration is not enabled",
              },
            ],
          },
        }),
      });
    },
  );
});

feature.afterEach(({ networkRequests }) => {
  networkRequests.splice(0, networkRequests.length);
});

test.describe("FieldmarkImportDialog", () => {
  test.describe("accessibility", () => {
    feature(
      "should not have any accessibility issues when open",
      async ({ Given, When, Then }) => {
        await Given["the fieldmark import dialog is mounted"]();
        await Then["there shouldn't be any axe violations"]();
      },
    );
  });

  test.describe("notebook fetching", () => {
    feature(
      "should fetch and display notebooks when opened",
      async ({ Given, When, Then }) => {
        await Given["the fieldmark import dialog is mounted"]();
        await When["the notebooks have been fetched"]();
        await Then["the notebooks should be displayed in the table"]();
      },
    );
  });

  test.describe("notebook import", () => {
    feature(
      "should make an import request when a notebook is selected and imported",
      async ({ Given, When, Then }) => {
        await Given["the fieldmark import dialog is mounted"]();
        await When["the notebooks have been fetched"]();
        await When["the user selects a notebook and clicks import"]();
        Then[
          "an import request should be made to the server with the correct notebook ID"
        ]();
      },
    );

    feature(
      "should make an import request without identifier column when notebook has no identifier columns",
      async ({ Given, When, Then }) => {
        await Given["the fieldmark import dialog is mounted"]();
        await When["the notebooks have been fetched"]();
        await When[
          "the user selects a notebook with no identifier columns and clicks import"
        ]();
        Then["an import request should be made without an identifier column"]();
      },
    );

    feature(
      "should make an import request with selected identifier column when notebook has identifier columns",
      async ({ Given, When, Then }) => {
        await Given["the fieldmark import dialog is mounted"]();
        await When["the notebooks have been fetched"]();
        await When[
          "the user selects a notebook with identifier columns, selects an identifier column, and clicks import"
        ]();
        Then[
          "an import request should be made with the selected identifier column"
        ]();
      },
    );

    feature(
      "should show importing alert during import process",
      async ({ Given, When, Then, router }) => {
        await router.route(
          "/api/inventory/v1/import/fieldmark/notebook",
          async (route) => {
            // Add a longer delay to ensure importing alert is visible long enough for tests
            await sleep(2500);
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                containerName: "Test Container from Test Notebook 1",
                containerGlobalId: "IC123456",
              }),
            });
          },
        );
        await Given["the fieldmark import dialog is mounted"]();
        await When["the notebooks have been fetched"]();
        await When[
          "the user selects a notebook and clicks import for alert testing"
        ]();
        await Then["an importing alert should be visible"]();
      },
    );

    feature(
      "should show success alert after import completes",
      async ({ Given, When, Then }) => {
        await Given["the fieldmark import dialog is mounted"]();
        await When["the notebooks have been fetched"]();
        await When[
          "the user selects a notebook and clicks import for alert testing"
        ]();
        await Then["a success alert should be visible"]();
      },
    );

    feature(
      "should show loading state on import button during import",
      async ({ Given, When, Then, router }) => {
        await router.route(
          "/api/inventory/v1/import/fieldmark/notebook",
          async (route) => {
            // Add a longer delay to ensure importing alert is visible long enough for tests
            await sleep(2500);
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                containerName: "Test Container from Test Notebook 1",
                containerGlobalId: "IC123456",
              }),
            });
          },
        );
        await Given["the fieldmark import dialog is mounted"]();
        await When["the notebooks have been fetched"]();
        await When[
          "the user selects a notebook and clicks import for alert testing"
        ]();
        await Then["the import button should show loading state"]();
      },
    );

    feature(
      "should display IGSN message when igsnCandidateFields endpoint returns IGSN error",
      async ({ Given, When, Then }) => {
        await Given["the fieldmark import dialog is mounted"]();
        await When["the notebooks have been fetched"]();
        await When[
          "the user selects a notebook that will trigger IGSN error"
        ]();
        await Then["the IGSN message should be displayed"]();
      },
    );
  });
});
