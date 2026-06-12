import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/experimental-ct-react";
import * as Jwt from "jsonwebtoken";
import { ImportDialogStory } from "./ImportDialog.story";

/*
 * Only the browser-bound cases remain in Playwright. The bulk of the behavioural
 * coverage for this dialog now lives in the jsdom suite, `ImportDialog.test.tsx`.
 * The cases kept here cannot run faithfully in jsdom:
 *   - Tab-traversal keyboard selection (`page.keyboard.press("Tab")`)
 *   - opening the external "View on PubChem" tab (`target=_blank`)
 *   - the axe scan that runs WITHOUT excluding color-contrast (jsdom cannot
 *     compute rendered colours, so contrast rules silently do not run there)
 */
const feature = test.extend<{
  Given: {
    "that the ImportDialog is mounted": () => Promise<void>;
  };
  When: {
    "a search is performed that returns multiple results": () => Promise<void>;
    "a compound is selected": () => Promise<void>;
    "tab key is used to navigate to a compound card": () => Promise<void>;
    "tab key is used to navigate to a compound checkbox": () => Promise<void>;
    "enter key is pressed": () => Promise<void>;
    "space key is pressed": () => Promise<void>;
    "the 'View on PubChem' link is clicked": () => Promise<void>;
  };
  Then: {
    "there should be no axe violations": () => Promise<void>;
    "the compound should be selected": () => Promise<void>;
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
  When: async ({ page }, use) => {
    await use({
      "a search is performed that returns multiple results": async () => {
        const searchInput = page.getByRole("textbox");
        await searchInput.fill("multiple");
        await searchInput.press("Enter");
      },
      "a compound is selected": async () => {
        await page
          .getByRole("checkbox", { name: /select/i })
          .first()
          .click();
      },
      "tab key is used to navigate to a compound card": async () => {
        const button = page.getByLabel("Aspirin").getByRole("button");
        while (await button.evaluate((btn) => btn !== document.activeElement)) {
          await page.keyboard.press("Tab");
        }
      },
      "tab key is used to navigate to a compound checkbox": async () => {
        const checkbox = page.getByLabel("Aspirin").getByRole("checkbox");
        while (await checkbox.evaluate((input) => input !== document.activeElement)) {
          await page.keyboard.press("Tab");
        }
      },
      "enter key is pressed": async () => {
        await page.keyboard.press("Enter");
      },
      "space key is pressed": async () => {
        await page.keyboard.press("Space");
      },
      "the 'View on PubChem' link is clicked": async () => {
        const newPagePromise = page.context().waitForEvent("page");
        await page
          .getByRole("link", { name: /View on PubChem/i })
          .first()
          .click();
        const newPage = await newPagePromise;
        await newPage.close();
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "there should be no axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(accessibilityScanResults.violations).toEqual([]);
      },
      "the compound should be selected": async () => {
        const checkbox = page.getByRole("checkbox", { name: /select/i }).first();
        await expect(checkbox).toBeChecked();
      },
    });
  },
  // biome-ignore lint/correctness/noEmptyPattern: Playwright fixture takes no destructured deps
  networkRequests: async ({}, use) => {
    await use([]);
  },
});
feature.beforeEach(async ({ router, page, networkRequests }) => {
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
  await router.route("/api/v1/pubchem/search", async (route) => {
    const requestData = JSON.parse(route.request().postData() || "{}") as {
      searchTerm?: string;
    };

    const searchTerm = requestData.searchTerm;
    if (searchTerm === "multiple") {
      const multipleResults = [
        {
          name: "Aspirin",
          pngImage: "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l",
          smiles: "CC(=O)OC1=CC=CC=C1C(=O)O",
          formula: "C9H8O4",
          pubchemId: "2244",
          pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/2244",
          cas: "50-78-2",
        },
        {
          name: "Paracetamol",
          pngImage: "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=1983&t=l",
          smiles: "CC(=O)NC1=CC=C(O)C=C1",
          formula: "C8H9NO2",
          pubchemId: "1983",
          pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/1983",
          cas: "103-90-2",
        },
      ];
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(multipleResults),
      });
    } else {
      const searchResults = [
        {
          name: "Aspirin",
          pngImage: "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l",
          smiles: "CC(=O)OC1=CC=CC=C1C(=O)O",
          formula: "C9H8O4",
          pubchemId: "2244",
          pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/2244",
          cas: "50-78-2",
        },
      ];
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(searchResults),
      });
    }
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
  feature("Should have no axe violations.", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be no axe violations"]();
  });
  feature(
    "Should allow keyboard selection of compounds by pressing enter on the card",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search is performed that returns multiple results"]();
      await When["tab key is used to navigate to a compound card"]();
      await When["enter key is pressed"]();
      await Then["the compound should be selected"]();
    },
  );
  feature(
    "Should allow keyboard selection of compounds by pressing space on the checkbox",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search is performed that returns multiple results"]();
      await When["tab key is used to navigate to a compound checkbox"]();
      await When["space key is pressed"]();
      await Then["the compound should be selected"]();
    },
  );
  feature("Should not toggle selection when clicking on external links", async ({ Given, When, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await When["a search is performed that returns multiple results"]();
    await When["a compound is selected"]();
    await When["the 'View on PubChem' link is clicked"]();
    await Then["the compound should be selected"]();
    /*
     * Clicking a link inside of the interactive card should not toggle the
     * selection state. Having interactive items be nested is less than ideal,
     * given the potentially issues with misclicking but given that a misclick
     * would only toggle the selected state which can be easily reverted this
     * was not considered worth foregoing the convenience of having the link
     * inside the card.
     */
  });
});
