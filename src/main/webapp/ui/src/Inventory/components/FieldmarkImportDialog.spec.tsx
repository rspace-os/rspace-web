import { expect, test } from "@playwright/experimental-ct-react";
import * as Jwt from "jsonwebtoken";
import { FieldmarkImportDialogStory } from "./FieldmarkImportDialog.story";

/*
 * The bulk of this component's behaviour (dialog open/close, notebook
 * rendering, import endpoint wiring, the importing alert, button loading state,
 * the IGSN message, hiding the identifier UI during import, and axe) is covered
 * by the jsdom unit test in FieldmarkImportDialog.test.tsx.
 *
 * The two cases that remain here interact with the toast/alert stack, which is
 * rendered `position: fixed` with a negative top offset, partially off the
 * top of the viewport. Asserting that the expand toggle is clickable and that
 * the revealed sub-message link is genuinely visible depends on real element
 * geometry and scroll position, so these stay in Playwright. They use the
 * viewport-geometry helpers `clickWhenInViewport` and
 * `moveToastStackIntoViewport` to bring the toast into view before
 * interacting / asserting.
 */

/**
 * Scrolls the fixed, partially off-screen toast stack fully into the viewport
 * so its controls/links can be clicked and asserted as visible. The toast
 * container is anchored to the top of the viewport with a negative offset, so
 * without this its top edge sits above the visible area.
 */
async function moveToastStackIntoViewport(page: import("@playwright/test").Page) {
  await page.getByTestId("Toasts").evaluate((el: HTMLElement) => {
    el.style.top = "0px";
    el.scrollIntoView({ block: "start" });
  });
}

/**
 * Clicks a locator only once it is confirmed to be within the viewport bounds,
 * scrolling it into view first. Guards against the flakiness of clicking a
 * control whose real geometry places it (partly) outside the viewport.
 */
async function clickWhenInViewport(locator: import("@playwright/test").Locator, browserName: string) {
  await expect(locator).toBeVisible();
  if (browserName === "webkit") {
    await clickWithWebKitDomFallback(locator);
    return;
  }
  await locator.scrollIntoViewIfNeeded();
  await expect(locator).toBeInViewport();
  await locator.click();
}

async function clickWithWebKitDomFallback(locator: import("@playwright/test").Locator) {
  await locator.evaluate((el: HTMLElement) => {
    el.scrollIntoView({ block: "center", inline: "center" });
    el.click();
  });
}

async function clickControl(locator: import("@playwright/test").Locator, browserName: string) {
  await expect(locator).toBeVisible();
  if (browserName === "webkit") {
    await clickWithWebKitDomFallback(locator);
    return;
  }
  await locator.click();
}

async function checkControl(locator: import("@playwright/test").Locator, browserName: string) {
  await expect(locator).toBeVisible();
  if (browserName === "webkit") {
    await clickWithWebKitDomFallback(locator);
    return;
  }
  await locator.check();
}

const feature = test.extend<{
  Given: {
    "the fieldmark import dialog is mounted": () => Promise<void>;
  };
  When: {
    "the notebooks have been fetched": () => Promise<void>;
    "the user selects a notebook and clicks import for alert testing": () => Promise<void>;
    "the user selects a notebook that will trigger detailed import error": () => Promise<void>;
  };
  Then: {
    "a success alert should be visible": () => Promise<void>;
    "a detailed error alert should be visible": () => Promise<void>;
  };
  networkRequests: Array<{ url: URL; postData: string | null }>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the fieldmark import dialog is mounted": async () => {
        await mount(<FieldmarkImportDialogStory />);
      },
    });
  },
  When: async ({ page, browserName }, use) => {
    await use({
      "the notebooks have been fetched": async () => {
        const dialog = page.getByRole("dialog");

        await expect(dialog).toBeVisible();
        // Wait for the DataGrid to appear
        const dataGrid = page.getByRole("grid");
        await expect(dataGrid).toBeVisible();
      },
      "the user selects a notebook and clicks import for alert testing": async () => {
        await checkControl(page.getByRole("radio", { name: "Select notebook: Test Notebook 1" }), browserName);
        await clickControl(page.getByRole("button", { name: "Import" }), browserName);
      },
      "the user selects a notebook that will trigger detailed import error": async () => {
        await checkControl(
          page.getByRole("radio", {
            name: "Select notebook: Notebook Detailed Error",
          }),
          browserName,
        );
        const identifierSelect = page.getByRole("combobox", {
          name: "IGSN ID field",
        });
        await clickControl(identifierSelect, browserName);
        if (browserName === "webkit") {
          await identifierSelect.focus();
          await page.keyboard.press("ArrowDown");
          await page.keyboard.press("Enter");
        } else {
          await clickControl(page.getByRole("option", { name: "identifier_field" }), browserName);
        }
        await clickControl(page.getByRole("button", { name: "Import" }), browserName);
      },
    });
  },
  Then: async ({ page, browserName }, use) => {
    await use({
      "a success alert should be visible": async () => {
        await moveToastStackIntoViewport(page);
        const alert = page.getByRole("alert").filter({ hasText: "Successfully imported notebook" });
        await expect(alert).toBeVisible();
        await expect(alert).toContainText("Successfully imported notebook");
        await clickWhenInViewport(alert.getByRole("button", { name: "1 sub-messages. Toggle to show" }), browserName);
        const subMessage = alert.getByRole("alert").filter({ hasText: "Test Container from Test Notebook 1" });
        const link = subMessage.getByRole("link", { name: "IC123456" });
        await expect(link).toBeVisible();
        await expect(link).toHaveAttribute("href", "/globalId/IC123456");
      },
      "a detailed error alert should be visible": async () => {
        await moveToastStackIntoViewport(page);
        const alert = page.getByRole("alert").filter({ hasText: "Could not import notebook." });
        await expect(alert).toBeVisible();

        await expect(alert).toContainText("Could not import notebook.");
        // Click the toggle button to expand the sub-messages with detailed errors
        await clickWhenInViewport(alert.getByRole("button", { name: "2 sub-messages. Toggle to show" }), browserName);
        // Verify each detailed error message is shown in the dropdown
        const firstErrorMessage = alert.getByRole("alert").filter({
          hasText:
            'Error importing notebook "1726126204618-rspace-igsn-demo" from Fieldmark: Unable to find an existing assignable identifier: 10.82316/mq1c-b544',
        });

        await expect(firstErrorMessage).toBeVisible();
        const secondErrorMessage = alert.getByRole("alert").filter({
          hasText: "Additional validation error: Sample template validation failed",
        });
        await expect(secondErrorMessage).toBeVisible();
      },
    });
  },
  // biome-ignore lint/correctness/noEmptyPattern: Playwright fixture takes no destructured deps
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
        {
          name: "Notebook Detailed Error",
          metadata: {
            project_id: "test-project-detailed-error",
            ispublic: false,
            pre_description: "A notebook that triggers detailed import error",
            project_lead: "Test User 6",
          },
          status: "draft",
        },
      ]),
    });
  });
  await router.route("/api/inventory/v1/import/fieldmark/notebook", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        containerName: "Test Container from Test Notebook 1",
        containerGlobalId: "IC123456",
      }),
    });
  });
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
  await router.route(
    "/api/inventory/v1/fieldmark/notebooks/igsnCandidateFields?notebookId=test-project-detailed-error",
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(["identifier_field"]),
      });
    },
  );
});
feature.afterEach(({ networkRequests }) => {
  networkRequests.splice(0, networkRequests.length);
});
test.describe("FieldmarkImportDialog", () => {
  test.describe("notebook import", () => {
    feature("should show success alert after import completes", async ({ Given, When, Then }) => {
      await Given["the fieldmark import dialog is mounted"]();
      await When["the notebooks have been fetched"]();
      await When["the user selects a notebook and clicks import for alert testing"]();
      await Then["a success alert should be visible"]();
    });
    feature(
      "should show detailed error message when import fails with validation errors",
      async ({ Given, When, Then, router }) => {
        await router.route("/api/inventory/v1/import/fieldmark/notebook", async (route) => {
          const request = route.request();

          const postData = request.postData();
          if (postData?.includes('"notebookId":"test-project-detailed-error"')) {
            await route.fulfill({
              status: 400,
              contentType: "application/json",
              body: JSON.stringify({
                status: "BAD_REQUEST",
                httpCode: 400,
                internalCode: 40002,
                message: "Errors detected : 2",
                messageCode: null,
                errors: [
                  'fieldmarkApiImportRequest: Error importing notebook "1726126204618-rspace-igsn-demo" from Fieldmark: Unable to find an existing assignable identifier: 10.82316/mq1c-b544',
                  "fieldmarkApiImportRequest: Additional validation error: Sample template validation failed",
                ],
                iso8601Timestamp: "2025-08-07T08:40:52.973881769Z",
                data: {
                  validationErrors: [
                    {
                      field: null,
                      rejectedValue: null,
                      objectName: "fieldmarkApiImportRequest",
                      message:
                        'Error importing notebook "1726126204618-rspace-igsn-demo" from Fieldmark: Unable to find an existing assignable identifier: 10.82316/mq1c-b544',
                    },
                    {
                      field: null,
                      rejectedValue: null,
                      objectName: "fieldmarkApiImportRequest",
                      message: "Additional validation error: Sample template validation failed",
                    },
                  ],
                },
              }),
            });
          } else {
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                containerName: "Test Container from Test Notebook 1",
                containerGlobalId: "IC123456",
              }),
            });
          }
        });
        await Given["the fieldmark import dialog is mounted"]();
        await When["the notebooks have been fetched"]();
        await When["the user selects a notebook that will trigger detailed import error"]();
        await Then["a detailed error alert should be visible"]();
      },
    );
  });
});
