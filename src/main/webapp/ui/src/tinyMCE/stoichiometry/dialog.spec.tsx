import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import {
  StoichiometryDialogWithCalculateButtonStory,
  StoichiometryDialogWithTableStory,
} from "./dialog.story";
import * as Jwt from "jsonwebtoken";

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

const createOnChangesUpdateSpy = () => {
  let currentValue = false;
  let callCount = 0;

  const handler = (hasChanges: boolean) => {
    currentValue = hasChanges;
    callCount++;
  };

  const getCurrentValue = () => currentValue;
  const getCallCount = () => callCount;
  const hasBeenCalled = () => callCount > 0;

  return {
    handler,
    getCurrentValue,
    getCallCount,
    hasBeenCalled,
  };
};

const createOnSaveSpy = () => {
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

const createOnDeleteSpy = () => {
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
    "the dialog is open with a stoichiometry table": ({
      onSaveSpy,
      onDeleteSpy,
    }?: {
      onSaveSpy?: ReturnType<typeof createOnSaveSpy>;
      onDeleteSpy?: ReturnType<typeof createOnDeleteSpy>;
    }) => Promise<void>;
  };
  When: {
    "the user clicks calculate": () => Promise<void>;
    "the user edits a cell in the table": () => Promise<void>;
    "the user changes the limiting reagent": () => Promise<void>;
    "the user saves the changes": () => Promise<void>;
    "the user clicks the delete button": () => Promise<void>;
    "the user confirms the deletion": () => Promise<void>;
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
    "the save button should not be visible": () => Promise<void>;
    "the save button should be visible": () => Promise<void>;
    "a PUT request should have been made to update the stoichiometry table": () => void;
    "a DELETE request should have been made to delete the stoichiometry table": () => void;
    "the delete button should be visible": () => Promise<void>;
    "the confirmation dialog should be displayed": () => Promise<void>;
    "the table should be hidden": () => Promise<void>;
  };
  networkRequests: Array<{ url: URL; postData: string | null; method: string }>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the dialog is open without a stoichiometry table": async (spies) => {
        const onTableCreatedSpy = spies?.onTableCreatedSpy;
        await mount(
          <StoichiometryDialogWithCalculateButtonStory
            onTableCreated={onTableCreatedSpy?.handler}
          />,
        );
      },
      "the dialog is open with a stoichiometry table": async (spies) => {
        const onSaveSpy = spies?.onSaveSpy;
        const onDeleteSpy = spies?.onDeleteSpy;
        await mount(
          <StoichiometryDialogWithTableStory
            onSave={onSaveSpy?.handler}
            onDelete={onDeleteSpy?.handler}
          />,
        );
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
      "the user edits a cell in the table": async () => {
        const table = page.getByRole("grid");

        const indexOfNotesColumn = await Promise.all(
          (await table.getByRole("columnheader").all()).map((cell) =>
            cell.textContent(),
          ),
        ).then((textOfCells) =>
          textOfCells.findIndex((text) => /notes/i.test(text ?? "")),
        );

        await table
          .getByRole("row")
          .nth(1)
          .getByRole("gridcell")
          .nth(indexOfNotesColumn)
          .dblclick();

        // Wait for input to appear and be focused
        const input = page.locator('input[type="text"]');
        await input.waitFor({ state: "visible" });
        await input.fill("Test note");
        await input.press("Enter");
      },
      "the user changes the limiting reagent": async () => {
        const cyclopentadieneRadio = page.getByRole("radio", {
          name: /Select Cyclopentadiene as limiting reagent/,
        });
        await cyclopentadieneRadio.click();
      },
      "the user saves the changes": async () => {
        await page.getByRole("button", { name: "Save Changes" }).click();
      },
      "the user clicks the delete button": async () => {
        await page.getByRole("button", { name: "Delete" }).click();
      },
      "the user confirms the deletion": async () => {
        const confirmButton = page.getByRole("button", { name: "Delete" });
        await confirmButton.click();
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
      "a POST request should have been made to create the stoichiometry table":
        () => {
          const postRequest = networkRequests.find(
            (request) =>
              request.url.pathname.includes("/api/v1/stoichiometry") &&
              request.postData !== null,
          );
          expect(postRequest).toBeDefined();
        },
      "the save button should not be visible": async () => {
        const saveButton = page.getByTestId("SubmitButton");
        await expect(saveButton).not.toBeVisible();
      },
      "the save button should be visible": async () => {
        const saveButton = page.getByRole("button", { name: "Save Changes" });
        await expect(saveButton).toBeVisible();
      },
      "a PUT request should have been made to update the stoichiometry table":
        () => {
          const putRequest = networkRequests.find(
            (request) =>
              request.url.pathname.includes("/api/v1/stoichiometry") &&
              request.method === "PUT",
          );
          expect(putRequest).toBeDefined();
        },
      "a DELETE request should have been made to delete the stoichiometry table":
        () => {
          const deleteRequest = networkRequests.find(
            (request) =>
              request.url.pathname.includes("/api/v1/stoichiometry") &&
              request.method === "DELETE",
          );
          expect(deleteRequest).toBeDefined();
        },
      "the delete button should be visible": async () => {
        const deleteButton = page.getByRole("button", { name: "Delete" });
        await expect(deleteButton).toBeVisible();
      },
      "the confirmation dialog should be displayed": async () => {
        const confirmDialog = page.getByRole("dialog", {
          name: /Delete Stoichiometry Table/,
        });
        await expect(confirmDialog).toBeVisible();
      },
      "the table should be hidden": async () => {
        const table = page.getByRole("grid");
        await expect(table).not.toBeVisible();
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

  const mockResponse = {
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
  };

  await router.route("/api/v1/stoichiometry*", (route) => {
    const method = route.request().method();

    if (method === "GET" || method === "POST" || method === "PUT") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockResponse),
      });
    }

    if (method === "DELETE") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ success: true }),
      });
    }

    throw new Error("Other methods are not supported");
  });

  await router.route("/integration/integrationInfo?name=CHEMISTRY", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          name: "CHEMISTRY",
          displayName: "Chemistry",
          available: true,
          enabled: true,
          oauthConnected: false,
          options: {},
        },
        error: null,
        success: true,
        errorMsg: null,
      }),
    });
  });

  page.on("request", (request) => {
    networkRequests.push({
      url: new URL(request.url()),
      postData: request.postData(),
      method: request.method(),
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
      Then["the callback should have been invoked"]({
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
      Then[
        "a POST request should have been made to create the stoichiometry table"
      ]();
    },
  );

  feature(
    "does not show save button when table has not been modified",
    async ({ Given, Then }) => {
      await Given["the dialog is open with a stoichiometry table"]();
      await Then["the table is displayed"]();
      await Then["the save button should not be visible"]();
    },
  );

  feature(
    "shows save button when table data is modified by editing a cell",
    async ({ Given, When, Then }) => {
      await Given["the dialog is open with a stoichiometry table"]();
      await Then["the table is displayed"]();
      await When["the user edits a cell in the table"]();
      await Then["the save button should be visible"]();
    },
  );

  feature(
    "shows save button when limiting reagent is changed",
    async ({ Given, When, Then }) => {
      await Given["the dialog is open with a stoichiometry table"]();
      await Then["the table is displayed"]();
      await When["the user changes the limiting reagent"]();
      await Then["the save button should be visible"]();
    },
  );

  feature(
    "hides save button after saving changes",
    async ({ Given, When, Then }) => {
      await Given["the dialog is open with a stoichiometry table"]();
      await Then["the table is displayed"]();
      await When["the user edits a cell in the table"]();
      await When["the user saves the changes"]();
      await Then["the save button should not be visible"]();
    },
  );

  feature(
    "makes a PUT API call when saving changes to the table",
    async ({ Given, When, Then }) => {
      await Given["the dialog is open with a stoichiometry table"]();
      await Then["the table is displayed"]();
      await When["the user edits a cell in the table"]();
      await When["the user saves the changes"]();
      Then[
        "a PUT request should have been made to update the stoichiometry table"
      ]();
    },
  );

  feature(
    "shows delete button when table is present",
    async ({ Given, Then }) => {
      await Given["the dialog is open with a stoichiometry table"]();
      await Then["the table is displayed"]();
      await Then["the delete button should be visible"]();
    },
  );

  feature(
    "shows confirmation dialog when delete button is clicked",
    async ({ Given, When, Then }) => {
      await Given["the dialog is open with a stoichiometry table"]();
      await Then["the table is displayed"]();
      await When["the user clicks the delete button"]();
      await Then["the confirmation dialog should be displayed"]();
    },
  );

  feature(
    "makes a DELETE API call and hides table when deletion is confirmed",
    async ({ Given, When, Then }) => {
      await Given["the dialog is open with a stoichiometry table"]();
      await Then["the table is displayed"]();
      await When["the user clicks the delete button"]();
      await When["the user confirms the deletion"]();
      Then[
        "a DELETE request should have been made to delete the stoichiometry table"
      ]();
      await Then["the table should be hidden"]();
    },
  );
});
