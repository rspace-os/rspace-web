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
    "a search is performed that returns multiple results": () => Promise<void>;
    "the search type selector is clicked": () => Promise<void>;
    "SMILES is chosen as the search type": () => Promise<void>;
    "the import button is clicked without selecting any compounds": () => Promise<void>;
    "the escape key is pressed to dismiss the validation warning": () => Promise<void>;
    "a compound is selected": () => Promise<void>;
    "a selected compound is clicked again": () => Promise<void>;
    "tab key is used to navigate to a compound card": () => Promise<void>;
    "tab key is used to navigate to a compound checkbox": () => Promise<void>;
    "enter key is pressed": () => Promise<void>;
    "space key is pressed": () => Promise<void>;
    "the 'View on PubChem' link is clicked": () => Promise<void>;
    "a search fails": () => Promise<void>;
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
    "a validation warning should be shown": () => Promise<void>;
    "the validation warning should disappear after selecting a compound": () => Promise<void>;
    "the single result should be selected by default": () => Promise<void>;
    "multiple results should not be selected by default": () => Promise<void>;
    "the compound should not be selected": () => Promise<void>;
    "the compound should be selected": () => Promise<void>;
    "an error alert should be shown": () => Promise<void>;
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
        const searchInput = page.getByRole("textbox");
        await searchInput.fill("aspirin");
        await searchInput.press("Enter");
      },
      "a search is performed that returns multiple results": async () => {
        const searchInput = page.getByRole("textbox");
        await searchInput.fill("multiple");
        await searchInput.press("Enter");
      },
      "the search type selector is clicked": async () => {
        const searchTypeSelector = page.getByRole("combobox", {
          name: "Search type",
        });
        await searchTypeSelector.click();
      },
      "SMILES is chosen as the search type": async () => {
        const searchTypeSelector = page.getByRole("combobox", {
          name: "Search type",
        });
        await searchTypeSelector.click();
        await page
          .getByRole("option", {
            name: "SMILES",
          })
          .click();
      },
      "the import button is clicked without selecting any compounds":
        async () => {
          await page.getByRole("button", { name: /import selected/i }).click();
        },
      "the escape key is pressed to dismiss the validation warning":
        async () => {
          await page.keyboard.press("Escape");
        },
      "a compound is selected": async () => {
        await page
          .getByRole("checkbox", { name: /select/i })
          .first()
          .click();
      },
      "a selected compound is clicked again": async () => {
        const checkbox = page
          .getByRole("checkbox", { name: /select/i })
          .first();
        await checkbox.click();
      },
      "tab key is used to navigate to a compound card": async () => {
        const button = page.getByLabel("Aspirin").getByRole("button");
        while (await button.evaluate((btn) => btn !== document.activeElement)) {
          await page.keyboard.press("Tab");
        }
      },
      "tab key is used to navigate to a compound checkbox": async () => {
        const checkbox = page.getByLabel("Aspirin").getByRole("checkbox");
        while (
          await checkbox.evaluate((input) => input !== document.activeElement)
        ) {
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
      "a search fails": async () => {
        const searchInput = page.getByRole("textbox");
        await searchInput.fill("error");
        await searchInput.press("Enter");
      },
    });
  },
  Then: async ({ page, networkRequests }, use) => {
    await use({
      "there should be a dialog visible": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();
      },
      "the single result should be selected by default": async () => {
        const checkbox = page.getByRole("checkbox", {
          name: /select compound/i,
        });
        await expect(checkbox).toBeChecked();
      },
      "multiple results should not be selected by default": async () => {
        const checkboxes = page
          .getByRole("checkbox", { name: /select compound/i })
          .all();
        for (const checkbox of await checkboxes) {
          await expect(checkbox).not.toBeChecked();
        }
      },
      "there should be a search type selector": async () => {
        const searchTypeSelector = page.getByRole("combobox", {
          name: "Search type",
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
        const searchInput = page.getByRole("textbox");
        await expect(searchInput).toBeVisible();
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
      "a validation warning should be shown": async () => {
        const alert = page.getByRole("alert");
        await expect(alert).toBeVisible();
        await expect(alert).toHaveText(/please select at least one compound/i);
      },
      "the validation warning should disappear after selecting a compound":
        async () => {
          const alert = page.getByRole("alert");
          await expect(alert).not.toBeVisible();
        },
      "the compound should not be selected": async () => {
        const checkbox = page
          .getByRole("checkbox", { name: /select/i })
          .first();
        await expect(checkbox).not.toBeChecked();
      },
      "the compound should be selected": async () => {
        const checkbox = page
          .getByRole("checkbox", { name: /select/i })
          .first();
        await expect(checkbox).toBeChecked();
      },
      "an error alert should be shown": async () => {
        const alert = page.getByRole("alert");
        await expect(alert).toBeVisible();
        await expect(alert).toHaveText(/There was an error/);
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
    const requestData = JSON.parse(route.request().postData() || "{}");
    const searchTerm = requestData.searchTerm;

    if (searchTerm === "error") {
      return route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ message: "There was an error" }),
      });
    } else if (searchTerm === "multiple") {
      const multipleResults = [
        {
          name: "Aspirin",
          pngImage:
            "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l",
          smiles: "CC(=O)OC1=CC=CC=C1C(=O)O",
          formula: "C9H8O4",
          pubchemId: "2244",
          pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/2244",
          cas: "50-78-2",
        },
        {
          name: "Paracetamol",
          pngImage:
            "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=1983&t=l",
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
          pngImage:
            "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l",
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

  feature(
    "Should auto-select a compound when there is only one result",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search is performed"]();
      await Then["the single result should be selected by default"]();
      /*
       * The vast majority of the time there will only be one result, so
       * auto-selecting it is a small usability improvement that reduces
       * the friction to inserting compounds, especially when paired with
       * the slash menu command.
       */
    }
  );

  feature(
    "Should not auto-select compounds when there are multiple results",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search is performed that returns multiple results"]();
      await Then["multiple results should not be selected by default"]();
    }
  );

  feature(
    "Should toggle compound selection when clicked twice",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search is performed that returns multiple results"]();
      await When["a compound is selected"]();
      await When["a selected compound is clicked again"]();
      await Then["the compound should not be selected"]();
    }
  );

  feature(
    "Should allow keyboard selection of compounds by pressing enter on the card",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search is performed that returns multiple results"]();
      await When["tab key is used to navigate to a compound card"]();
      await When["enter key is pressed"]();
      await Then["the compound should be selected"]();
    }
  );

  feature(
    "Should allow keyboard selection of compounds by pressing space on the checkbox",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search is performed that returns multiple results"]();
      await When["tab key is used to navigate to a compound checkbox"]();
      await When["space key is pressed"]();
      await Then["the compound should be selected"]();
    }
  );

  feature(
    "Should not toggle selection when clicking on external links",
    async ({ Given, When, Then }) => {
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
    }
  );

  feature(
    "Should validate compound selection",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search is performed that returns multiple results"]();
      await When[
        "the import button is clicked without selecting any compounds"
      ]();
      await Then["a validation warning should be shown"]();
      await When[
        "the escape key is pressed to dismiss the validation warning"
      ]();
      await When["a compound is selected"]();
      await Then[
        "the validation warning should disappear after selecting a compound"
      ]();
    }
  );

  feature(
    "An error when searching should result in an alert toast",
    async ({ Given, When, Then }) => {
      await Given["that the ImportDialog is mounted"]();
      await When["a search fails"]();
      await Then["an error alert should be shown"]();
    }
  );
});
