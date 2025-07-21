import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { NewNoteStory } from "./NewNote.story";
import AxeBuilder from "@axe-core/playwright";

const createCallbackSpy = () => {
  let called = false;
  let lastCalledWith: unknown;

  const handler = (...args: Array<unknown>) => {
    called = true;
    lastCalledWith = args;
  };

  const asyncHandler = (...args: Array<unknown>) => {
    called = true;
    lastCalledWith = args;
    return Promise.resolve();
  };

  const hasBeenCalled = () => called;

  return {
    handler,
    asyncHandler,
    hasBeenCalled,
    toHaveBeenLastCalledWith: () => lastCalledWith,
  };
};

const feature = test.extend<{
  Given: {
    "that the new note field has been mounted": () => Promise<{
      onErrorStateChangeSpy: ReturnType<typeof createCallbackSpy>;
      createNoteSpy: ReturnType<typeof createCallbackSpy>;
    }>;
    "that the user has typed some text": (text: string) => Promise<void>;
    "that the user has typed a note that exceeds the character limit": () => Promise<void>;
  };
  Once: {};
  When: {
    "the user types in the note field": (text: string) => Promise<void>;
    "the user removes all of the text": () => Promise<void>;
    "the user clicks the create note button": () => Promise<void>;
  };
  Then: {
    "there shouldn't be any axe violations": () => Promise<void>;
    "the note field should be empty": () => Promise<void>;
    "there should be an error message": (message: string) => Promise<void>;
    "there should not be an error message": () => Promise<void>;
    "createNote should be triggered": ({
      createNoteSpy,
    }: {
      createNoteSpy: ReturnType<typeof createCallbackSpy>;
    }) => Promise<void>;
    "onErrorStateChange should be called with": (
      hasError: boolean,
      {
        onErrorStateChangeSpy,
      }: { onErrorStateChangeSpy: ReturnType<typeof createCallbackSpy> },
    ) => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount, page }, use) => {
    await use({
      "that the new note field has been mounted": async () => {
        const onErrorStateChangeSpy = createCallbackSpy();
        const createNoteSpy = createCallbackSpy();
        await mount(
          <NewNoteStory
            onErrorStateChange={onErrorStateChangeSpy.handler}
            createNote={createNoteSpy.asyncHandler}
          />,
        );
        return { onErrorStateChangeSpy, createNoteSpy };
      },
      "that the user has typed some text": async (text: string) => {
        const frame = page.frameLocator("iframe");
        await frame
          .locator('body[contenteditable="true"]')
          .pressSequentially(text);
      },
      "that the user has typed a note that exceeds the character limit":
        async () => {
          const longText = "a".repeat(2001);
          const frame = page.frameLocator("iframe");
          await frame
            .locator('body[contenteditable="true"]')
            .pressSequentially(longText);
        },
    });
  },
  Once: async ({ page }, use) => {
    await use({});
  },
  When: async ({ page }, use) => {
    await use({
      "the user types in the note field": async (text: string) => {
        const frame = page.frameLocator("iframe");
        await frame
          .locator('body[contenteditable="true"]')
          .pressSequentially("Your note text here");
      },
      "the user clicks the create note button": async () => {
        await page.getByRole("button", { name: "Create note" }).click();
      },
      "the user removes all of the text": async () => {
        const frame = page.frameLocator("iframe");
        await frame.locator('body[contenteditable="true"]').fill("");
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
             * 1. MUI DataGrid renders its immediate children with role=presentation,
             *    which Firefox considers to be a violation
             * 2. Component tests don't have main landmarks as they're isolated components
             * 3. Component tests typically don't have h1 headings as they're not full pages
             * 4. Content not in landmarks is expected in component testing context
             */
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
      "the note field should be empty": async () => {
        const frame = page.frameLocator("iframe");
        await expect(frame.locator('body[contenteditable="true"]')).toHaveText(
          "",
        );
      },
      "there should be an error message": async (message: string) => {
        await page.getByRole("button", { name: "Create note" }).click();
        const popover = page.getByRole("dialog");
        await expect(popover).toBeVisible();
        await expect(popover).toHaveText(message);
        await page.keyboard.press("Escape");
      },
      "there should not be an error message": async () => {
        await page.getByRole("button", { name: "Create note" }).click();
        const popover = page.getByRole("dialog");
        await expect(popover).not.toBeVisible();
        await page.keyboard.press("Escape");
      },
      "createNote should be triggered": async ({ createNoteSpy }) => {
        expect(createNoteSpy.hasBeenCalled()).toBe(true);
      },
      "onErrorStateChange should be called with": async (
        hasError: boolean,
        {
          onErrorStateChangeSpy,
        }: { onErrorStateChangeSpy: ReturnType<typeof createCallbackSpy> },
      ) => {
        expect(onErrorStateChangeSpy.hasBeenCalled()).toBe(true);
        expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([
          hasError,
        ]);
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

test.describe("NewNote", () => {
  feature("Has no accessibility violations", async ({ Given, Then }) => {
    await Given["that the new note field has been mounted"]();
    await Then["there shouldn't be any axe violations"]();
  });

  feature("Validates empty notes", async ({ Given, When, Then }) => {
    await Given["that the new note field has been mounted"]();
    await Then["there should be an error message"]("Note cannot be empty.");
  });

  feature(
    "Validates notes that exceed character limit",
    async ({ Given, Then }) => {
      await Given["that the new note field has been mounted"]();
      await Given[
        "that the user has typed a note that exceeds the character limit"
      ]();
      await Then["there should be an error message"](
        "Note cannot exceed 2000 characters.",
      );
    },
  );

  feature(
    "Successfully creates a note with valid input",
    async ({ Given, When, Then }) => {
      const { onErrorStateChangeSpy, createNoteSpy } =
        await Given["that the new note field has been mounted"]();
      await When["the user types in the note field"]("This is a valid note");
      await Then["onErrorStateChange should be called with"](false, {
        onErrorStateChangeSpy,
      });
      await When["the user clicks the create note button"]();
      await Then["createNote should be triggered"]({ createNoteSpy });
    },
  );

  feature(
    "Handles transitioning from valid to invalid state",
    async ({ Given, When, Then }) => {
      const { onErrorStateChangeSpy } =
        await Given["that the new note field has been mounted"]();
      await Given["that the user has typed some text"]("Valid note");
      await Then["onErrorStateChange should be called with"](false, {
        onErrorStateChangeSpy,
      });
      await When["the user removes all of the text"]();
      await Then["onErrorStateChange should be called with"](true, {
        onErrorStateChangeSpy,
      });
      await Then["there should be an error message"]("Note cannot be empty.");
    },
  );

  feature(
    "Handles transitioning from invalid to valid state",
    async ({ Given, When, Then }) => {
      const { onErrorStateChangeSpy } =
        await Given["that the new note field has been mounted"]();
      await Given["that the user has typed some text"]("Valid note");
      await When["the user removes all of the text"]();
      await Then["onErrorStateChange should be called with"](true, {
        onErrorStateChangeSpy,
      });
      await Then["there should be an error message"]("Note cannot be empty.");
      await Given["that the user has typed some text"]("Valid note");
      await Then["onErrorStateChange should be called with"](false, {
        onErrorStateChangeSpy,
      });
      await Then["there should not be an error message"]();
    },
  );

  feature(
    "Resets field after successful note creation",
    async ({ Given, When, Then, page }) => {
      const { onErrorStateChangeSpy } =
        await Given["that the new note field has been mounted"]();
      await Given["that the user has typed some text"]("Test note content");
      await When["the user clicks the create note button"]();
      await Then["the note field should be empty"]();
      await Then["onErrorStateChange should be called with"](false, {
        onErrorStateChangeSpy,
      });
    },
  );
});
