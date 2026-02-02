import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import AxeBuilder from "@axe-core/playwright";
import Jwt from "jsonwebtoken";
import {
  CallableSnippetPreviewStory,
  CallableSnippetPreviewWithTableContent,
  CallableSnippetPreviewWithError,
} from "./CallableSnippetPreview.story";
const feature = test.extend<{
  Given: {
    "the snippet preview component is mounted": () => Promise<void>;
    "the snippet preview with table content is mounted": () => Promise<void>;
    "the snippet preview with error is mounted": () => Promise<void>;
  };
  When: {
    "the user clicks the open snippet button": () => Promise<void>;
    "the user clicks the close button": () => Promise<void>;
    "the user presses the Escape key": () => Promise<void>;
  };
  Then: {
    "the preview dialog should be visible": () => Promise<void>;
    "the preview dialog should not be visible": () => Promise<void>;
    "the dialog should show loading state": () => Promise<void>;
    "the dialog should show snippet content": () => Promise<void>;
    "the dialog should show error message": () => Promise<void>;
    "the dialog should show table content correctly": () => Promise<void>;
    "the dialog should be accessible": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the snippet preview component is mounted": async () => {
        await mount(<CallableSnippetPreviewStory />);
      },
      "the snippet preview with table content is mounted": async () => {
        await mount(<CallableSnippetPreviewWithTableContent />);
      },
      "the snippet preview with error is mounted": async () => {
        await mount(<CallableSnippetPreviewWithError />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user clicks the open snippet button": async () => {
        await page
          .getByRole("button", { name: /open.*snippet.*preview/i })
          .click();
      },
      "the user clicks the close button": async () => {
        await page.getByRole("button", { name: /close/i }).click();
      },
      "the user presses the Escape key": async () => {
        await page.keyboard.press("Escape");
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the preview dialog should be visible": async () => {
        await expect(page.getByRole("dialog")).toBeVisible({ timeout: 5000 });
        await expect(page.getByText(/snippet preview:/i)).toBeVisible();
      },
      "the preview dialog should not be visible": async () => {
        await expect(page.getByRole("dialog")).not.toBeVisible({
          timeout: 5000,
        });
      },
      "the dialog should show loading state": async () => {
        await expect(page.getByText(/loading snippet content/i)).toBeVisible({
          timeout: 1000,
        });
      },
      "the dialog should show snippet content": async () => {
        await expect(page.getByText(/test snippet content/i)).toBeVisible({
          timeout: 10000,
        });
      },
      "the dialog should show error message": async () => {
        await expect(
          page.getByText(/error.*failed to load snippet content/i),
        ).toBeVisible({ timeout: 10000 });
      },
      "the dialog should show table content correctly": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog.locator("table")).toBeVisible();
        await expect(dialog.locator("th")).toHaveCount(3);
        await expect(dialog.locator("td")).toHaveCount(6);
        await expect(dialog.getByText("Header 1")).toBeVisible();
        await expect(dialog.getByText("Cell 1")).toBeVisible();
      },
      "the dialog should be accessible": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();
        const dialogTitle = page.getByText(/snippet preview:/i);
        await expect(dialogTitle).toBeVisible();
        const closeButton = page.getByRole("button", { name: /close/i });
        await expect(closeButton).toBeVisible();
      },
      "there shouldn't be any axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(
          accessibilityScanResults.violations.filter((v) => {
            return (
              v.description !==
                "Ensure elements with an ARIA role that require child roles contain them" &&
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region"
            );
          }),
        ).toEqual([]);
      },
    });
  },
});
feature.beforeEach(async ({ router }) => {
  await router.route("/session/ajax/analyticsProperties", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        analyticsEnabled: false,
      }),
    });
  });
  await router.route("/userform/ajax/preference*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
    });
  });
  await router.route("/deploymentproperties/ajax/property*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(false),
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
  await router.route("/api/v1/snippets/123/content", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "text/html",
      body: "<p>Test snippet content</p>",
    });
  });
  await router.route("/api/v1/snippets/124/content", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "text/html",
      body: `
        <table>
          <thead>
            <tr>
              <th>Header 1</th>
              <th>Header 2</th>
              <th>Header 3</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Cell 1</td>
              <td>Cell 2</td>
              <td>Cell 3</td>
            </tr>
            <tr>
              <td>Cell 4</td>
              <td>Cell 5</td>
              <td>Cell 6</td>
            </tr>
          </tbody>
        </table>
      `,
    });
  });
  await router.route("/api/v1/snippets/999/content", (route) => {
    return route.fulfill({
      status: 500,
      contentType: "application/json",
      body: JSON.stringify({
        message: "Failed to load snippet content",
        error: "Internal Server Error",
      }),
    });
  });
});
test.describe("CallableSnippetPreview", () => {
  test.describe("Dialog opening and closing", () => {
    feature(
      "Should open the preview dialog when triggered",
      async ({ Given, When, Then }) => {
        await Given["the snippet preview component is mounted"]();
        await When["the user clicks the open snippet button"]();
        await Then["the preview dialog should be visible"]();
      },
    );
    feature(
      "Should close the dialog when close button is clicked",
      async ({ Given, When, Then }) => {
        await Given["the snippet preview component is mounted"]();
        await When["the user clicks the open snippet button"]();
        await Then["the preview dialog should be visible"]();
        await When["the user clicks the close button"]();
        await Then["the preview dialog should not be visible"]();
      },
    );
    feature(
      "Should close the dialog when Escape key is pressed",
      async ({ Given, When, Then }) => {
        await Given["the snippet preview component is mounted"]();
        await When["the user clicks the open snippet button"]();
        await Then["the preview dialog should be visible"]();
        await When["the user presses the Escape key"]();
        await Then["the preview dialog should not be visible"]();
      },
    );
  });
  test.describe("Content rendering", () => {
    feature(
      "Should show loading state initially",
      async ({ Given, When, Then, page }) => {
        await Given["the snippet preview component is mounted"]();
        // Intercept the request to delay it
        await page.route("/api/v1/snippets/123/content", async (route) => {
          await new Promise((resolve) => setTimeout(resolve, 1000));
          return route.fulfill({
            status: 200,
            contentType: "text/html",
            body: "<p>Test snippet content</p>",
          });
        });
        await When["the user clicks the open snippet button"]();
        await Then["the dialog should show loading state"]();
      },
    );
    feature(
      "Should display snippet content after loading",
      async ({ Given, When, Then }) => {
        await Given["the snippet preview component is mounted"]();
        await When["the user clicks the open snippet button"]();
        await Then["the dialog should show snippet content"]();
      },
    );
    feature(
      "Should render HTML tables correctly",
      async ({ Given, When, Then }) => {
        await Given["the snippet preview with table content is mounted"]();
        await When["the user clicks the open snippet button"]();
        await Then["the dialog should show table content correctly"]();
      },
    );
    feature(
      "Should display error message when loading fails",
      async ({ Given, When, Then }) => {
        await Given["the snippet preview with error is mounted"]();
        await When["the user clicks the open snippet button"]();
        await Then["the dialog should show error message"]();
      },
    );
  });
  test.describe("Accessibility", () => {
    feature(
      "Should be accessible when opened",
      async ({ Given, When, Then }) => {
        await Given["the snippet preview component is mounted"]();
        await When["the user clicks the open snippet button"]();
        await Then["the dialog should be accessible"]();
      },
    );
    feature("Should have no axe violations", async ({ Given, When, Then }) => {
      await Given["the snippet preview component is mounted"]();
      await When["the user clicks the open snippet button"]();
      await Then["there shouldn't be any axe violations"]();
    });
  });
});
});
