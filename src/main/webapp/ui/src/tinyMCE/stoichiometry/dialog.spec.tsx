import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import {
  StoichiometryDialogWithCalculateButtonStory,
  StoichiometryDialogWithTableStory,
} from "./dialog.story";

const createOnTableCreatedSpy = () => {
  let called = false;

  const handler = () => {
    called = true;
  };

  const hasBeenCalled = () => called;

  return {
    handler,
    hasBeenCalled,
  };
};

const feature = test.extend<{
  Given: {
    "the dialog is open without a stoichiometry table": ({
      onTableCreatedSpy,
    }?: {
      onTableCreatedSpy?: ReturnType<typeof createOnTableCreatedSpy>;
    }) => Promise<void>;
    "the dialog is open with a stoichiometry table": () => Promise<void>;
  };
  When: {
    "the user clicks calculate": () => Promise<void>;
  };
  Then: {
    "the calculate button is visible": () => Promise<void>;
    "the table is displayed": () => Promise<void>;
    "the callback should have been invoked": ({
      onTableCreatedSpy,
    }: {
      onTableCreatedSpy: ReturnType<typeof createOnTableCreatedSpy>;
    }) => void;
    "a POST request should have been made to create the stoichiometry table": () => void;
  };
  networkRequests: Array<{ url: URL; postData: string | null }>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the dialog is open without a stoichiometry table": async ({
        onTableCreatedSpy,
      } = {}) => {
        await mount(
          <StoichiometryDialogWithCalculateButtonStory
            onTableCreated={onTableCreatedSpy?.handler}
          />,
        );
      },
      "the dialog is open with a stoichiometry table": async () => {
        await mount(<StoichiometryDialogWithTableStory />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user clicks calculate": async () => {
        const button = page.getByRole("button", {
          name: "Calculate Stoichiometry",
        });
        await button.click();
      },
    });
  },
  Then: async ({ page, networkRequests }, use) => {
    await use({
      "the calculate button is visible": async () => {
        const button = page.getByRole("button", {
          name: "Calculate Stoichiometry",
        });
        await expect(button).toBeVisible();
      },
      "the table is displayed": async () => {
        const table = page.getByRole("grid");
        await expect(table).toBeVisible();
      },
      "the callback should have been invoked": ({ onTableCreatedSpy }) => {
        expect(onTableCreatedSpy.hasBeenCalled()).toBe(true);
      },
      "a POST request should have been made to create the stoichiometry table": () => {
        const postRequest = networkRequests.find(
          (request) =>
            request.url.pathname.includes("/chemical/stoichiometry") &&
            request.postData !== null
        );
        expect(postRequest).toBeDefined();
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router, page, networkRequests }) => {
  await router.route("/chemical/stoichiometry*", (route) => {
    const mockResponse = {
      data: {
        id: 3,
        parentReaction: {
          id: 32769,
          parentId: 226,
          ecatChemFileId: null,
          dataImage: "mock-image-data",
          chemElements: "mock-chem-elements",
          smilesString: "C1=CC=CC=C1.C1C=CC=C1>>C1CCCCC1",
          chemId: null,
          reactionId: null,
          rgroupId: null,
          metadata: "{}",
          chemElementsFormat: "KET",
          creationDate: 1753964538000,
          imageFileProperty: {},
        },
        molecules: [
          {
            id: 4,
            rsChemElement: {
              id: 32770,
              parentId: null,
              ecatChemFileId: null,
              dataImage: null,
              chemElements: "C1=CC=CC=C1",
              smilesString: null,
              chemId: null,
              reactionId: null,
              rgroupId: null,
              metadata: null,
              chemElementsFormat: "MOL",
              creationDate: 1753964548124,
              imageFileProperty: null,
            },
            role: "REACTANT",
            formula: "C6 H6",
            name: "Benzene",
            smiles: "C1=CC=CC=C1",
            coefficient: 1.0,
            molecularWeight: 78.11,
            mass: null,
            moles: null,
            expectedAmount: null,
            actualAmount: null,
            actualYield: null,
            limitingReagent: false,
            notes: null,
          },
          {
            id: 5,
            rsChemElement: {
              id: 32771,
              parentId: null,
              ecatChemFileId: null,
              dataImage: null,
              chemElements: "C1C=CC=C1",
              smilesString: null,
              chemId: null,
              reactionId: null,
              rgroupId: null,
              metadata: null,
              chemElementsFormat: "MOL",
              creationDate: 1753964548126,
              imageFileProperty: null,
            },
            role: "REACTANT",
            formula: "C5 H6",
            name: "Cyclopentadiene",
            smiles: "C1C=CC=C1",
            coefficient: 1.0,
            molecularWeight: 66.1,
            mass: null,
            moles: null,
            expectedAmount: null,
            actualAmount: null,
            actualYield: null,
            limitingReagent: false,
            notes: null,
          },
          {
            id: 6,
            rsChemElement: {
              id: 32772,
              parentId: null,
              ecatChemFileId: null,
              dataImage: null,
              chemElements: "C1CCCCC1",
              smilesString: null,
              chemId: null,
              reactionId: null,
              rgroupId: null,
              metadata: null,
              chemElementsFormat: "MOL",
              creationDate: 1753964548127,
              imageFileProperty: null,
            },
            role: "PRODUCT",
            formula: "C6 H12",
            name: "Cyclohexane",
            smiles: "C1CCCCC1",
            coefficient: 1.0,
            molecularWeight: 84.16,
            mass: null,
            moles: null,
            expectedAmount: null,
            actualAmount: null,
            actualYield: null,
            limitingReagent: false,
            notes: null,
          },
        ],
      },
    };

    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(mockResponse),
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

test.describe("Stoichiometry Dialog", () => {
  feature(
    "shows calculate button when no table is present",
    async ({ Given, Then }) => {
      await Given["the dialog is open without a stoichiometry table"]();
      await Then["the calculate button is visible"]();
    },
  );

  feature(
    "displays stoichiometry table when data is available",
    async ({ Given, Then }) => {
      await Given["the dialog is open with a stoichiometry table"]();
      await Then["the table is displayed"]();
    },
  );

  feature(
    "invokes callback when table is successfully created",
    async ({ Given, When, Then }) => {
      const onTableCreatedSpy = createOnTableCreatedSpy();
      await Given["the dialog is open without a stoichiometry table"]({
        onTableCreatedSpy,
      });
      await When["the user clicks calculate"]();
      await Then["the table is displayed"]();
      await Then["the callback should have been invoked"]({
        onTableCreatedSpy,
      });
    },
  );

  feature(
    "makes a POST API call when creating a new table",
    async ({ Given, When, Then }) => {
      await Given["the dialog is open without a stoichiometry table"]();
      await When["the user clicks calculate"]();
      await Then["the table is displayed"]();
      Then["a POST request should have been made to create the stoichiometry table"]();
    },
  );
});
