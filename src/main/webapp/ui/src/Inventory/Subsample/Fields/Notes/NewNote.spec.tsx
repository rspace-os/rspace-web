import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { NewNoteStory } from "./NewNote.story";
import AxeBuilder from "@axe-core/playwright";

const createCallbackSpy = () => {
  let called = false;
  let allCalls: Array<Array<unknown>> = [];

  const handler = (...args: Array<unknown>) => {
    called = true;
    allCalls.push(args);
  };

  const asyncHandler = (...args: Array<unknown>) => {
    called = true;
    allCalls.push(args);
    return Promise.resolve();
  };

  const hasBeenCalled = () => called;

  return {
    handler,
    asyncHandler,
    hasBeenCalled,
    toHaveBeenLastCalledWith: () => allCalls[allCalls.length - 1],
    getAllCalls: () => allCalls,
    getCallCount: () => allCalls.length,
  };
};

const createFlagSpy = ({ initialValue }: { initialValue: boolean }) => {
  let value = initialValue;
  let setIsCalled = false;
  let unsetIsCalled = false;
  return {
    set: () => {
      setIsCalled = true;
      value = true;
    },
    unset: () => {
      unsetIsCalled = true;
      value = false;
    },
    isSet: () => value,
    isUnset: () => !value,
    hasBeenSet: () => setIsCalled,
    hasBeenUnset: () => unsetIsCalled,
  };
};

const feature = test.extend<{
  Given: {
    "that the new note field has been mounted": () => Promise<{
      onErrorStateChangeSpy: ReturnType<typeof createCallbackSpy>;
      createNoteSpy: ReturnType<typeof createCallbackSpy>;
      dirtyFlagSpy: ReturnType<typeof createFlagSpy>;
    }>;
    "that the new note field has been mounted with non-editable record": () => Promise<{
      onErrorStateChangeSpy: ReturnType<typeof createCallbackSpy>;
      createNoteSpy: ReturnType<typeof createCallbackSpy>;
    }>;
    "that the user has typed some text": (text: string) => Promise<void>;
    "that the user has typed a note that exceeds the character limit": () => Promise<void>;
    "that the new note field has been mounted whilst editing the whole record": () => Promise<{
      dirtyFlagSpy: ReturnType<typeof createFlagSpy>;
    }>;
  };
  Once: {};
  When: {
    "the user types in the note field": (text: string) => Promise<void>;
    "the user removes all of the text": () => Promise<void>;
    "the user clicks the create note button": () => Promise<void>;
    "the user clicks the clear button": () => Promise<void>;
  };
  Then: {
    "there shouldn't be any axe violations": () => Promise<void>;
    "the note field should be empty": () => Promise<void>;
    "there should be an error message": (message: string) => Promise<void>;
    "there should not be an error message": () => Promise<void>;
    "the create note button should be enabled": () => Promise<void>;
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
    "onErrorStateChange should never be called with true during note creation flow": ({
      onErrorStateChangeSpy,
    }: {
      onErrorStateChangeSpy: ReturnType<typeof createCallbackSpy>;
    }) => Promise<void>;
    "the record should be dirty": ({
      dirtyFlagSpy,
    }: {
      dirtyFlagSpy: ReturnType<typeof createFlagSpy>;
    }) => void;
    "the record should not be dirty": ({
      dirtyFlagSpy,
    }: {
      dirtyFlagSpy: ReturnType<typeof createFlagSpy>;
    }) => void;
    "record's dirty flag should not change": ({
      dirtyFlagSpy,
    }: {
      dirtyFlagSpy: ReturnType<typeof createFlagSpy>;
    }) => void;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount, page }, use) => {
    await use({
      "that the new note field has been mounted": async () => {
        const onErrorStateChangeSpy = createCallbackSpy();
        const createNoteSpy = createCallbackSpy();
        const dirtyFlagSpy = createFlagSpy({ initialValue: false });
        await mount(
          <NewNoteStory
            onErrorStateChange={onErrorStateChangeSpy.handler}
            createNote={createNoteSpy.asyncHandler}
            setDirtyFlag={dirtyFlagSpy.set}
            unsetDirtyFlag={dirtyFlagSpy.unset}
          />,
        );
        return {
          onErrorStateChangeSpy,
          createNoteSpy,
          dirtyFlagSpy,
        };
      },
      "that the new note field has been mounted with non-editable record":
        async () => {
          const onErrorStateChangeSpy = createCallbackSpy();
          const createNoteSpy = createCallbackSpy();
          await mount(
            <NewNoteStory
              onErrorStateChange={onErrorStateChangeSpy.handler}
              createNote={createNoteSpy.asyncHandler}
              isEditable={false}
            />,
          );
          return { onErrorStateChangeSpy, createNoteSpy };
        },
      "that the new note field has been mounted whilst editing the whole record":
        async () => {
          const dirtyFlagSpy = createFlagSpy({ initialValue: false });
          await mount(
            <NewNoteStory
              state="editing"
              setDirtyFlag={dirtyFlagSpy.set}
              unsetDirtyFlag={dirtyFlagSpy.unset}
            />,
          );
          return { dirtyFlagSpy };
        },
      "that the user has typed some text": async (text: string) => {
        const frame = page.frameLocator("iframe");
        await frame
          .locator('body[contenteditable="true"]')
          .pressSequentially(text);
      },
      "that the user has typed a note that exceeds the character limit":
        async () => {
          const longText = "a".repeat(2010);
          const frame = page.frameLocator("iframe");
          /*
           * pressSequentially is too slow on jenkins, so we use fill, but fill
           * doesn't trigger all of the same events so we also use a single
           * pressSequentially to ensure that the dirty flag is set and the
           * error state is triggered.
           */
          await frame.locator('body[contenteditable="true"]').fill(longText);
          await frame
            .locator('body[contenteditable="true"]')
            .pressSequentially("a");
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
      "the user clicks the clear button": async () => {
        await page.getByRole("button", { name: "Clear", exact: true }).click();
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
      "the create note button should be enabled": async () => {
        await expect(
          page.getByRole("button", { name: "Create note" }),
        ).toBeEnabled();
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
      "onErrorStateChange should never be called with true during note creation flow":
        async ({
          onErrorStateChangeSpy,
        }: {
          onErrorStateChangeSpy: ReturnType<typeof createCallbackSpy>;
        }) => {
          const allCalls = onErrorStateChangeSpy.getAllCalls();
          expect(allCalls.length).toBeGreaterThanOrEqual(1);

          // Verify that no call was made with true (error state)
          const hasErrorCall = allCalls.some((call) => call[0] === true);
          expect(hasErrorCall).toBe(false);

          // All calls should be with false (no error)
          allCalls.forEach((call) => {
            expect(call).toEqual([false]);
          });
        },
      "the record should be dirty": ({
        dirtyFlagSpy,
      }: {
        dirtyFlagSpy: ReturnType<typeof createFlagSpy>;
      }) => {
        expect(dirtyFlagSpy.isSet()).toBe(true);
      },
      "the record should not be dirty": ({
        dirtyFlagSpy,
      }: {
        dirtyFlagSpy: ReturnType<typeof createFlagSpy>;
      }) => {
        expect(dirtyFlagSpy.isUnset()).toBe(true);
      },
      "record's dirty flag should not change": ({
        dirtyFlagSpy,
      }: {
        dirtyFlagSpy: ReturnType<typeof createFlagSpy>;
      }) => {
        expect(dirtyFlagSpy.hasBeenUnset()).toBe(false);
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

  test.describe("Validates notes that exceed character limit", () => {
    test.skip(
      ({ browserName }) => browserName === "firefox",
      "Skipped on Firefox due to TinyMCE performance issues when typing large amounts of text (2000+ characters). Firefox times out during the pressSequentially operation, while other browsers handle it fine.",
    );
    feature(
      "shows error for notes exceeding character limit",
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
  });

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

  feature("Handles non-editable state correctly", async ({ Given, Then }) => {
    await Given[
      "that the new note field has been mounted with non-editable record"
    ]();
    await Then["the create note button should be enabled"]();
    await Then["there should be an error message"]("Notes are not editable");
  });

  feature(
    "Does not trigger error state during programmatic reset after successful note creation",
    async ({ Given, When, Then }) => {
      /*
       * This test verifies that when the field is programmatically reset after
       * successful note creation, onErrorStateChange is never called with true.
       * The component uses isResettingRef to prevent showing an error state
       * when setNote("") is called programmatically, which would normally
       * trigger validation showing "Note cannot be empty".
       */
      const { onErrorStateChangeSpy } =
        await Given["that the new note field has been mounted"]();
      await Given["that the user has typed some text"]("Valid note");
      await When["the user clicks the create note button"]();
      await Then[
        "onErrorStateChange should never be called with true during note creation flow"
      ]({
        onErrorStateChangeSpy,
      });
    },
  );

  feature(
    "Clear button clears the field and resets error state",
    async ({ Given, When, Then }) => {
      const { onErrorStateChangeSpy } =
        await Given["that the new note field has been mounted"]();
      await Given["that the user has typed some text"]("Test note");
      await Then["onErrorStateChange should be called with"](false, {
        onErrorStateChangeSpy,
      });
      await When["the user clicks the clear button"]();
      await Then["the note field should be empty"]();
      await Then["onErrorStateChange should be called with"](false, {
        onErrorStateChangeSpy,
      });
    },
  );

  feature(
    "Clear button works when field is in error state",
    async ({ Given, When, Then }) => {
      const { onErrorStateChangeSpy } =
        await Given["that the new note field has been mounted"]();
      await Given["that the user has typed some text"]("Valid note");
      await When["the user removes all of the text"]();
      await Then["onErrorStateChange should be called with"](true, {
        onErrorStateChangeSpy,
      });
      await When["the user clicks the clear button"]();
      await Then["the note field should be empty"]();
      await Then["onErrorStateChange should be called with"](false, {
        onErrorStateChangeSpy,
      });
    },
  );

  feature(
    "Clear button is idempotent when field is already empty",
    async ({ Given, When, Then }) => {
      const { onErrorStateChangeSpy } =
        await Given["that the new note field has been mounted"]();
      await Then["the note field should be empty"]();
      await When["the user clicks the clear button"]();
      await Then["the note field should be empty"]();
      await Then["onErrorStateChange should be called with"](false, {
        onErrorStateChangeSpy,
      });
      await When["the user clicks the clear button"]();
      await Then["the note field should be empty"]();
      await Then["onErrorStateChange should be called with"](false, {
        onErrorStateChangeSpy,
      });
    },
  );

  test.describe("dirty flag", () => {
    feature(
      "Manages dirty flag correctly when the subsample is in preview",
      async ({ Given, When, Then }) => {
        const { dirtyFlagSpy } =
          await Given["that the new note field has been mounted"]();
        await When["the user types in the note field"]("test");
        Then["the record should be dirty"]({
          dirtyFlagSpy,
        });
        await When["the user clicks the create note button"]();
        Then["the record should not be dirty"]({ dirtyFlagSpy });
      },
    );
    feature(
      "Does not manage dirty flag when the subsample is in editing",
      async ({ Given, When, Then }) => {
        const { dirtyFlagSpy } =
          await Given[
            "that the new note field has been mounted whilst editing the whole record"
          ]();
        await When["the user types in the note field"]("test");
        Then["the record should be dirty"]({
          dirtyFlagSpy,
        });
        await When["the user clicks the create note button"]();
        Then["record's dirty flag should not change"]({
          dirtyFlagSpy,
        });
      },
      /*
       * When the user is editing the whole subsample, the dirty flag is set by
       * the new note component but it is not unset. If we unset the dirty flag
       * but the user has made other changes, then the dirty flag will not
       * alert them of unsaved changes. This does mean that if the only change
       * the user has made is to the new note field, then the dirty flag will
       * not be unset and they will get a false warning about unsaved changes
       * when there are none.
       */
    );
  });
});
