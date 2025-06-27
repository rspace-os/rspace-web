import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { ImportDialogStory } from "./ImportDialog.story";
import AxeBuilder from "@axe-core/playwright";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "that the ImportDialog is mounted": () => Promise<void>;
  };
  Once: Record<string, never>;
  When: {
    "the cancel button is clicked": () => Promise<void>;
    "a search is performed": () => Promise<void>;
    "the search type selector is clicked": () => Promise<void>;
    "SMILES is chosen as the search type": () => Promise<void>;
  };
  Then: {
    "there should be a dialog visible": () => Promise<void>;
    "there should be no axe violations": () => Promise<void>;
    "there should be a dialog header banner": () => Promise<void>;
    "there should be a title: 'Import from PubChem'": () => Promise<void>;
    "the dialog is closed": () => Promise<void>;
    "there should be a search input": () => Promise<void>;
    "the mocked results are shown": () => Promise<void>;
    "there should be a search type selector": () => Promise<void>;
    "SMILES is passed in the API call": () => void;
  };
  networkRequests: Array<{ url: URL; postData: string | null }>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "that the ImportDialog is mounted": async () => {
        await mount(<ImportDialogStory />);
      },
    });
  },
  Once: async ({}, use) => {
    await use({});
  },
  When: async ({ page }, use) => {
    await use({
      "the cancel button is clicked": async () => {
        const dialog = page.getByRole("dialog");
        const closeButton = dialog.getByRole("button", { name: /cancel/i });
        await closeButton.click();
      },
      "a search is performed": async () => {
        const searchInput = page.getByRole("search");
        await searchInput.fill("aspirin");
        await searchInput.press("Enter");
      },
      "the search type selector is clicked": async () => {
        const searchTypeSelector = page.getByRole("combobox", {
          name: "Name/CAS",
        });
        await searchTypeSelector.click();
      },
      "SMILES is chosen as the search type": async () => {
        const searchTypeSelector = page.getByRole("combobox", {
          name: "Name/CAS",
        });
        await searchTypeSelector.click();
        await page
          .getByRole("option", {
            name: "SMILES",
          })
          .click();
      },
    });
  },
  Then: async ({ page, networkRequests }, use) => {
    await use({
      "there should be a dialog visible": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();
      },
      "there should be a search type selector": async () => {
        const searchTypeSelector = page.getByRole("combobox", {
          name: "Name/CAS",
        });
        await expect(searchTypeSelector).toBeVisible();
      },
      "there should be no axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(accessibilityScanResults.violations).toEqual([]);
      },
      "there should be a dialog header banner": async () => {
        const dialogHeader = page.getByRole("banner", {
          name: "dialog header",
        });
        await expect(dialogHeader).toBeVisible();
        await expect(dialogHeader).toHaveText("PubChem");
      },
      "there should be a title: 'Import from PubChem'": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog).toHaveAccessibleName("Import from PubChem");
        const title = dialog.getByRole("heading", { level: 3 });
        await expect(title).toBeVisible();
        await expect(title).toHaveText("Import from PubChem");
      },
      "the dialog is closed": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog).not.toBeVisible();
      },
      "there should be a search input": async () => {
        const searchInput = page.getByRole("search");
        await expect(searchInput).toBeVisible();
        await expect(searchInput).toHaveAttribute(
          "placeholder",
          "Enter a compound name, CAS number, or SMILES"
        );
      },
      "the mocked results are shown": async () => {
        const searchResults = page.getByRole("region", {
          name: /Search Results/i,
        });
        await expect(searchResults).toHaveText(/Aspirin/);
      },
      "SMILES is passed in the API call": () => {
        const searchRequest = networkRequests.find(
          (request) =>
            request.url.pathname === "/api/v1/chemical/search" &&
            request.postData?.includes('"searchType":"SMILES"')
        );
        expect(searchRequest).toBeDefined();
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router, page, networkRequests }) => {
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
  await router.route("/session/ajax/livechatProperties", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        liveChatEnabled: false,
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
          published: false,
          system: false,
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
  await router.route("/api/v1/chemical/search", async (route) => {
    const searchResults = [
      {
        name: "Aspirin",
        pngImage:
          "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l",
        smiles: "CC(=O)OC1=CC=CC=C1C(=O)O",
        formula: "C9H8O4",
        pubchemId: "2244",
        pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/2244",
      },
    ];
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(searchResults),
    });
  });

  page.on("request", (request) => {
    networkRequests.push({
      url: new URL(request.url()),
      postData: request.postData(),
    });
  });
});

feature.afterEach(({ networkRequests }) => {
  networkRequests.splice(0, networkRequests.length);
});

test.describe("ImportDialog", () => {
  feature("Renders correctly", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be a dialog visible"]();
  });
  feature("Should have no axe violations.", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be no axe violations"]();
  });
  feature("Should be a dialog header banner", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be a dialog header banner"]();
  });
  feature("Should have a title", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be a title: 'Import from PubChem'"]();
  });
  feature("Should have a a close button", async ({ Given, When, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await When["the cancel button is clicked"]();
    await Then["the dialog is closed"]();
  });
  feature("Should have a search input", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be a search input"]();
  });
  feature("Should have a search type selector", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be a search type selector"]();
  });
  feature(
    "The API endpoint is called when a search is performed",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search is performed"]();
      await Then["the mocked results are shown"]();
    }
  );
  feature("searchType is passed in API call", async ({ Given, When, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await When["SMILES is chosen as the search type"]();
    await When["a search is performed"]();
    Then["SMILES is passed in the API call"]();
  });
});
