import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import * as Jwt from "jsonwebtoken";
import AxeBuilder from "@axe-core/playwright";
import { backdropClasses } from "@mui/material/Backdrop";
import {
  MoveToS3DialogWithOneFile,
  MoveToS3DialogWithTwoFiles,
  MoveToS3DialogInTransferMode,
  MoveToS3DialogInTransferModeWithTwoFiles,
} from "./MoveToS3.story";

const S3_FILESTORE = {
  id: 1,
  name: "My S3 Bucket",
  fileSystem: { id: 10, name: "AWS S3", clientType: "S3" },
};

const IRODS_FILESTORE = {
  id: 2,
  name: "iRODS server",
  fileSystem: { id: 20, name: "iRODS", clientType: "IRODS" },
};

const OPERATION_SUCCESS_RESPONSE = {
  numFilesInput: 1,
  numFilesSucceed: 1,
  fileInfoDetails: [{ recordId: 123, fileName: "test.jpg", succeeded: true }],
};

const feature = test.extend<{
  Given: {
    "the Move to S3 dialog with one file is mounted": () => Promise<void>;
    "the Move to S3 dialog with two files is mounted": () => Promise<void>;
    "the Move to S3 dialog in transfer mode is mounted": () => Promise<void>;
    "the Move to S3 dialog in transfer mode with two files is mounted": () => Promise<void>;
  };
  When: {
    "the user opens the filestore dropdown and selects 'My S3 Bucket'": () => Promise<void>;
    "the user checks 'Retain a copy in RSpace'": () => Promise<void>;
    "the user checks 'Retain a copy on source bucket'": () => Promise<void>;
    "the user clicks the submit button": () => Promise<void>;
  };
  Then: {
    "the dialog title 'Move to S3' should be visible": () => Promise<void>;
    "the submit button should be disabled": () => Promise<void>;
    "the submit button should be enabled": () => Promise<void>;
    "the submit button should say 'Move'": () => Promise<void>;
    "the submit button should say 'Copy'": () => Promise<void>;
    "the submit button should say 'Transfer'": () => Promise<void>;
    "the transfer button should be disabled": () => Promise<void>;
    "the transfer button should be enabled": () => Promise<void>;
    "the 'no S3 filestore' message should be visible": () => Promise<void>;
    "the 'retain on source' checkbox should be visible": () => Promise<void>;
    "the move endpoint should have been called": () => void;
    "the copy endpoint should have been called": () => void;
    "the transfer endpoint should have been called": () => void;
    "a success alert should be visible": () => Promise<void>;
    "a copy success alert should be visible": () => Promise<void>;
    "a transfer success alert should be visible": () => Promise<void>;
    "the dialog title 'Transfer to S3' should be visible": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the Move to S3 dialog with one file is mounted": async () => {
        await mount(<MoveToS3DialogWithOneFile />);
      },
      "the Move to S3 dialog with two files is mounted": async () => {
        await mount(<MoveToS3DialogWithTwoFiles />);
      },
      "the Move to S3 dialog in transfer mode is mounted": async () => {
        await mount(<MoveToS3DialogInTransferMode />);
      },
      "the Move to S3 dialog in transfer mode with two files is mounted": async () => {
        await mount(<MoveToS3DialogInTransferModeWithTwoFiles />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user opens the filestore dropdown and selects 'My S3 Bucket'":
        async () => {
          await page
            .getByRole("button", { name: /select a filestore/i })
            .click();
          await page
            .getByRole("menuitem", { name: /my s3 bucket/i })
            .click();
        },
      "the user checks 'Retain a copy in RSpace'": async () => {
        await page
          .getByRole("checkbox", { name: /retain a copy in rspace/i })
          .click();
      },
      "the user checks 'Retain a copy on source bucket'": async () => {
        await page
          .getByRole("checkbox", { name: /retain a copy on source bucket/i })
          .click();
      },
      "the user clicks the submit button": async () => {
        await page
          .getByRole("button", { name: /^(move|copy|transfer)$/i })
          .click();
      },
    });
  },
  Then: async ({ page, networkRequests }, use) => {
    await use({
      "the dialog title 'Move to S3' should be visible": async () => {
        await expect(
          page.getByRole("heading", { name: /move to s3/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "the submit button should be disabled": async () => {
        await page
          .getByRole("button", { name: /select a filestore/i })
          .waitFor({ timeout: 5000 });
        await page.getByRole("button", { name: /^move$/i }).click();
        await expect(
          page.getByText(/a destination filestore is required/i),
        ).toBeVisible({ timeout: 5000 });
        // Dismiss the validation popover by clicking its backdrop
        await page.locator(`.${backdropClasses.root}`).last().click();
      },
      "the submit button should be enabled": async () => {
        await expect(
          page.getByRole("button", { name: /^move$/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "the submit button should say 'Move'": async () => {
        await expect(
          page.getByRole("button", { name: /^move$/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "the submit button should say 'Copy'": async () => {
        await expect(
          page.getByRole("button", { name: /^copy$/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "the submit button should say 'Transfer'": async () => {
        await expect(
          page.getByRole("button", { name: /^transfer$/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "the transfer button should be disabled": async () => {
        await page
          .getByRole("button", { name: /select a filestore/i })
          .waitFor({ timeout: 5000 });
        await page.getByRole("button", { name: /^transfer$/i }).click();
        await expect(
          page.getByText(/a destination filestore is required/i),
        ).toBeVisible({ timeout: 5000 });
        // Dismiss the validation popover by clicking its backdrop
        await page.locator(`.${backdropClasses.root}`).last().click();
      },
      "the transfer button should be enabled": async () => {
        await expect(
          page.getByRole("button", { name: /^transfer$/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "the 'no S3 filestore' message should be visible": async () => {
        await expect(
          page.getByText(/no s3 filestore has been configured/i),
        ).toBeVisible({ timeout: 5000 });
      },
      "the 'retain on source' checkbox should be visible": async () => {
        await expect(
          page.getByRole("checkbox", { name: /retain a copy on source bucket/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "the move endpoint should have been called": () => {
        const paths = networkRequests.map((url) => url.pathname);
        expect(paths).toContain("/api/v1/gallery/filestores/1/move");
      },
      "the copy endpoint should have been called": () => {
        const paths = networkRequests.map((url) => url.pathname);
        expect(paths).toContain("/api/v1/gallery/filestores/1/copy");
      },
      "the transfer endpoint should have been called": () => {
        const paths = networkRequests.map((url) => url.pathname);
        expect(paths).toContain("/api/v1/gallery/filestores/2/transfer");
      },
      "a success alert should be visible": async () => {
        await expect(
          page.getByText(/successfully moved/i),
        ).toBeVisible({ timeout: 5000 });
      },
      "a copy success alert should be visible": async () => {
        await expect(
          page.getByText(/successfully copied/i),
        ).toBeVisible({ timeout: 5000 });
      },
      "a transfer success alert should be visible": async () => {
        await expect(
          page.getByText(/successfully transferred/i),
        ).toBeVisible({ timeout: 5000 });
      },
      "the dialog title 'Transfer to S3' should be visible": async () => {
        await expect(
          page.getByRole("heading", { name: /transfer to s3/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "there shouldn't be any axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(
          accessibilityScanResults.violations.filter(
            (v) =>
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region" &&
              v.id !== "color-contrast" &&
              v.id !== "list",
          ),
        ).toEqual([]);
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router, page, networkRequests }) => {
  page.on("request", (request) => {
    networkRequests.push(new URL(request.url()));
  });

  await router.route("/session/ajax/analyticsProperties", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ analyticsEnabled: false }),
    }),
  );
  await router.route("/userform/ajax/preference*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
    }),
  );
  await router.route("/deploymentproperties/ajax/property*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(false),
    }),
  );
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
      body: JSON.stringify({ data: Jwt.sign(payload, "dummySecretKey") }),
    });
  });

  // Default: one S3 filestore available
  await router.route("/api/v1/gallery/filestores", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([S3_FILESTORE]),
    }),
  );
  await router.route("/api/v1/gallery/filestores/1/move", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(OPERATION_SUCCESS_RESPONSE),
    }),
  );
  await router.route("/api/v1/gallery/filestores/1/copy", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(OPERATION_SUCCESS_RESPONSE),
    }),
  );
  // Default transfer route: source filestore id=2 (as used in transfer mode stories)
  await router.route("/api/v1/gallery/filestores/2/transfer", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(OPERATION_SUCCESS_RESPONSE),
    }),
  );
});

feature.afterEach(({ networkRequests }) => {
  networkRequests.splice(0, networkRequests.length);
});

test.describe("MoveToS3", () => {
  test.describe("Accessibility", () => {
    test.setTimeout(30000);
    feature("Should have no axe violations", async ({ Given, Then }) => {
      await Given["the Move to S3 dialog with one file is mounted"]();
      await Then["the dialog title 'Move to S3' should be visible"]();
      await Then["there shouldn't be any axe violations"]();
    });
  });

  test.describe("Empty state", () => {
    feature(
      "When no filestores are configured shows a 'no S3 filestore' message",
      async ({ Given, Then, router }) => {
        await router.route("/api/v1/gallery/filestores", (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([]),
          }),
        );
        await Given["the Move to S3 dialog with one file is mounted"]();
        await Then["the 'no S3 filestore' message should be visible"]();
      },
    );

    feature(
      "When only non-S3 filestores are configured shows a 'no S3 filestore' message",
      async ({ Given, Then, router }) => {
        await router.route("/api/v1/gallery/filestores", (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([IRODS_FILESTORE]),
          }),
        );
        await Given["the Move to S3 dialog with one file is mounted"]();
        await Then["the 'no S3 filestore' message should be visible"]();
      },
    );
  });

  test.describe("Submit button state", () => {
    feature(
      "Submit button is disabled until a filestore is selected",
      async ({ Given, When, Then }) => {
        await Given["the Move to S3 dialog with one file is mounted"]();
        await Then["the dialog title 'Move to S3' should be visible"]();
        await Then["the submit button should be disabled"]();
        await When[
          "the user opens the filestore dropdown and selects 'My S3 Bucket'"
        ]();
        await Then["the submit button should be enabled"]();
      },
    );

    feature(
      "Submit button label switches from 'Move' to 'Copy' when retain-copy is checked",
      async ({ Given, When, Then }) => {
        await Given["the Move to S3 dialog with one file is mounted"]();
        await Then["the dialog title 'Move to S3' should be visible"]();
        await Then["the submit button should say 'Move'"]();
        await When["the user checks 'Retain a copy in RSpace'"]();
        await Then["the submit button should say 'Copy'"]();
      },
    );
  });

  test.describe("API calls", () => {
    feature(
      "Selecting a filestore and clicking Move calls the move endpoint",
      async ({ Given, When, Then }) => {
        await Given["the Move to S3 dialog with one file is mounted"]();
        await Then["the dialog title 'Move to S3' should be visible"]();
        await When[
          "the user opens the filestore dropdown and selects 'My S3 Bucket'"
        ]();
        await When["the user clicks the submit button"]();
        await Then["a success alert should be visible"]();
        Then["the move endpoint should have been called"]();
      },
    );

    feature(
      "Checking 'Retain a copy' and clicking Copy calls the copy endpoint",
      async ({ Given, When, Then }) => {
        await Given["the Move to S3 dialog with one file is mounted"]();
        await Then["the dialog title 'Move to S3' should be visible"]();
        await When[
          "the user opens the filestore dropdown and selects 'My S3 Bucket'"
        ]();
        await When["the user checks 'Retain a copy in RSpace'"]();
        await When["the user clicks the submit button"]();
        await Then["a copy success alert should be visible"]();
        Then["the copy endpoint should have been called"]();
      },
    );

    feature(
      "The correct record IDs are sent to the move endpoint",
      async ({ Given, When, Then, page }) => {
        await Given["the Move to S3 dialog with two files is mounted"]();
        await Then["the dialog title 'Move to S3' should be visible"]();
        await When[
          "the user opens the filestore dropdown and selects 'My S3 Bucket'"
        ]();

        const moveRequestPromise = page.waitForRequest(
          /\/api\/v1\/gallery\/filestores\/1\/move/,
        );
        await When["the user clicks the submit button"]();
        const moveRequest = await moveRequestPromise;
        await Then["a success alert should be visible"]();
        expect(
          (moveRequest.postDataJSON() as { recordIds: unknown }).recordIds,
        ).toEqual(expect.arrayContaining([123, 456]));
      },
    );
  });

  test.describe("Transfer mode", () => {
    feature(
      "Should have no axe violations in transfer mode",
      async ({ Given, Then }) => {
        test.setTimeout(30000);
        await Given["the Move to S3 dialog in transfer mode is mounted"]();
        await Then["the dialog title 'Transfer to S3' should be visible"]();
        await Then["there shouldn't be any axe violations"]();
      },
    );

    feature(
      "Transfer mode shows 'Transfer' button and 'retain on source bucket' checkbox",
      async ({ Given, Then }) => {
        await Given["the Move to S3 dialog in transfer mode is mounted"]();
        await Then["the dialog title 'Transfer to S3' should be visible"]();
        await Then["the submit button should say 'Transfer'"]();
        await Then["the 'retain on source' checkbox should be visible"]();
      },
    );

    feature(
      "Transfer button is disabled until a destination filestore is selected",
      async ({ Given, When, Then }) => {
        await Given["the Move to S3 dialog in transfer mode is mounted"]();
        await Then["the dialog title 'Transfer to S3' should be visible"]();
        await Then["the transfer button should be disabled"]();
        await When[
          "the user opens the filestore dropdown and selects 'My S3 Bucket'"
        ]();
        await Then["the transfer button should be enabled"]();
      },
    );

    feature(
      "Clicking Transfer calls the transfer endpoint and shows a success alert",
      async ({ Given, When, Then }) => {
        await Given["the Move to S3 dialog in transfer mode is mounted"]();
        await Then["the dialog title 'Transfer to S3' should be visible"]();
        await When[
          "the user opens the filestore dropdown and selects 'My S3 Bucket'"
        ]();
        await When["the user clicks the submit button"]();
        await Then["a transfer success alert should be visible"]();
        Then["the transfer endpoint should have been called"]();
      },
    );

    feature(
      "Transfer sends correct body with deleteSource=true by default",
      async ({ Given, When, page }) => {
        await Given["the Move to S3 dialog in transfer mode is mounted"]();
        await When[
          "the user opens the filestore dropdown and selects 'My S3 Bucket'"
        ]();

        const transferRequestPromise = page.waitForRequest(
          /\/api\/v1\/gallery\/filestores\/2\/transfer/,
        );
        await When["the user clicks the submit button"]();
        const transferRequest = await transferRequestPromise;
        expect(transferRequest.postDataJSON()).toMatchObject({
          sourcePath: "/data/file.jpg",
          destFilestoreId: 1,
          destPath: "file.jpg",
          deleteSource: true,
        });
      },
    );

    feature(
      "Checking 'Retain a copy on source bucket' sends deleteSource=false",
      async ({ Given, When, page }) => {
        await Given["the Move to S3 dialog in transfer mode is mounted"]();
        await When[
          "the user opens the filestore dropdown and selects 'My S3 Bucket'"
        ]();
        await When["the user checks 'Retain a copy on source bucket'"]();

        const transferRequestPromise = page.waitForRequest(
          /\/api\/v1\/gallery\/filestores\/2\/transfer/,
        );
        await When["the user clicks the submit button"]();
        const transferRequest = await transferRequestPromise;
        expect(transferRequest.postDataJSON()).toMatchObject({
          deleteSource: false,
        });
      },
    );

    feature(
      "All source paths are sent to the transfer endpoint for multiple files",
      async ({ Given, When, page }) => {
        await Given[
          "the Move to S3 dialog in transfer mode with two files is mounted"
        ]();
        await When[
          "the user opens the filestore dropdown and selects 'My S3 Bucket'"
        ]();

        const capturedBodies: unknown[] = [];
        await page.route(
          /\/api\/v1\/gallery\/filestores\/2\/transfer/,
          async (route) => {
            capturedBodies.push(route.request().postDataJSON());
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify(OPERATION_SUCCESS_RESPONSE),
            });
          },
        );

        await When["the user clicks the submit button"]();
        await expect(
          page.getByText(/successfully transferred/i).first(),
        ).toBeVisible({ timeout: 5000 });
        expect(capturedBodies).toHaveLength(2);
        expect(capturedBodies).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              sourcePath: "/data/file1.jpg",
              destPath: "file1.jpg",
            }),
            expect.objectContaining({
              sourcePath: "/data/file2.jpg",
              destPath: "file2.jpg",
            }),
          ]),
        );
      },
    );
  });
});
