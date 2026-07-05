import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { server } from "vitest/browser";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { NewNoteStory } from "../NewNote.story";
import { NewNotePage } from "./pageObjects/NewNotePage";

// ---------------------------------------------------------------------------
// Spy helpers
// ---------------------------------------------------------------------------

const createCallbackSpy = () => {
  let called = false;
  const allCalls: Array<Array<unknown>> = [];
  const handler = (...args: Array<unknown>) => {
    called = true;
    allCalls.push(args);
  };
  const asyncHandler = (...args: Array<unknown>) => {
    called = true;
    allCalls.push(args);
    return Promise.resolve();
  };
  return {
    handler,
    asyncHandler,
    hasBeenCalled: () => called,
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

// ---------------------------------------------------------------------------
// Shared setup
// ---------------------------------------------------------------------------

const notePage = new NewNotePage();

afterEach(() => {
  cleanup();
});

// ---------------------------------------------------------------------------
// Mount helpers
// ---------------------------------------------------------------------------

async function mountDefaultStory() {
  const onErrorStateChangeSpy = createCallbackSpy();
  const createNoteSpy = createCallbackSpy();
  const dirtyFlagSpy = createFlagSpy({ initialValue: false });
  render(
    <NewNoteStory
      onErrorStateChange={onErrorStateChangeSpy.handler}
      createNote={createNoteSpy.asyncHandler}
      setDirtyFlag={dirtyFlagSpy.set}
      unsetDirtyFlag={dirtyFlagSpy.unset}
    />,
  );
  // Wait for TinyMCE iframe + contenteditable body to appear before proceeding.
  await expect.element(notePage.editorBody).toBeVisible();
  return { onErrorStateChangeSpy, createNoteSpy, dirtyFlagSpy };
}

async function mountNonEditableStory() {
  const onErrorStateChangeSpy = createCallbackSpy();
  const createNoteSpy = createCallbackSpy();
  render(
    <NewNoteStory
      onErrorStateChange={onErrorStateChangeSpy.handler}
      createNote={createNoteSpy.asyncHandler}
      isEditable={false}
    />,
  );
  await expect.element(notePage.createNoteButton).toBeVisible();
  return { onErrorStateChangeSpy, createNoteSpy };
}

async function mountEditingStory() {
  const dirtyFlagSpy = createFlagSpy({ initialValue: false });
  render(<NewNoteStory state="editing" setDirtyFlag={dirtyFlagSpy.set} unsetDirtyFlag={dirtyFlagSpy.unset} />);
  await expect.element(notePage.editorBody).toBeVisible();
  return { dirtyFlagSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("NewNote", () => {
  test("Has no accessibility violations", async () => {
    await mountDefaultStory();
    await expectNoAxeViolations();
  });

  test("Validates empty notes", async () => {
    await mountDefaultStory();
    await notePage.expectErrorOnSubmit("Note cannot be empty");
  });

  describe("Validates notes that exceed character limit", () => {
    /*
     * Skip on Firefox: TinyMCE times out during fill+pressSequentially with
     * 2000+ characters. The other browsers handle it fine.
     */
    test.skipIf(server.browser === "firefox")("shows error for notes exceeding character limit", async () => {
      await mountDefaultStory();
      const longText = "a".repeat(2010);
      /*
       * `fill` alone is too fast and may not trigger TinyMCE's change
       * handler; a trailing `pressSequentially("a")` ensures TinyMCE fires
       * and the error state is set (matches the original spec's approach).
       */
      await notePage.fillEditorLong(longText);
      await notePage.expectErrorOnSubmit("Note cannot exceed");
    });
  });

  test("Successfully creates a note with valid input", async () => {
    const { onErrorStateChangeSpy, createNoteSpy } = await mountDefaultStory();
    await notePage.typeInEditor("Your note text here");
    // onErrorStateChange should have been called with false (no error)
    expect(onErrorStateChangeSpy.hasBeenCalled()).toBe(true);
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([false]);
    await notePage.clickCreateNote();
    expect(createNoteSpy.hasBeenCalled()).toBe(true);
  });

  test("Handles transitioning from valid to invalid state", async () => {
    const { onErrorStateChangeSpy } = await mountDefaultStory();
    await notePage.typeInEditor("Valid note");
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([false]);
    await notePage.clearEditor();
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([true]);
    await notePage.expectErrorOnSubmit("Note cannot be empty");
  });

  test("Handles transitioning from invalid to valid state", async () => {
    const { onErrorStateChangeSpy } = await mountDefaultStory();
    await notePage.typeInEditor("Valid note");
    await notePage.clearEditor();
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([true]);
    await notePage.expectErrorOnSubmit("Note cannot be empty");
    await notePage.typeInEditor("Valid note");
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([false]);
    await notePage.expectNoErrorOnSubmit();
  });

  test("Resets field after successful note creation", async () => {
    const { onErrorStateChangeSpy } = await mountDefaultStory();
    await notePage.typeInEditor("Test note content");
    await notePage.clickCreateNote();
    // After creation the editor should be empty
    await expect.element(notePage.editorBody).toHaveTextContent("");
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([false]);
  });

  test("Handles non-editable state correctly", async () => {
    await mountNonEditableStory();
    await expect.element(notePage.createNoteButton).toBeEnabled();
    await notePage.expectErrorOnSubmit("Notes are not editable");
  });

  test("Does not trigger error state during programmatic reset after successful note creation", async () => {
    /*
     * Verifies that when the field is programmatically reset after successful
     * note creation, onErrorStateChange is never called with true. The
     * component uses isResettingRef to prevent the "Note cannot be empty"
     * error state from appearing after a programmatic setNote("").
     */
    const { onErrorStateChangeSpy } = await mountDefaultStory();
    await notePage.typeInEditor("Valid note");
    await notePage.clickCreateNote();

    const allCalls = onErrorStateChangeSpy.getAllCalls();
    expect(allCalls.length).toBeGreaterThanOrEqual(1);
    const hasErrorCall = allCalls.some((call) => call[0] === true);
    expect(hasErrorCall).toBe(false);
    allCalls.forEach((call) => {
      expect(call).toEqual([false]);
    });
  });

  test("Clear button clears the field and resets error state", async () => {
    const { onErrorStateChangeSpy } = await mountDefaultStory();
    await notePage.typeInEditor("Test note");
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([false]);
    await notePage.clickClear();
    await expect.element(notePage.editorBody).toHaveTextContent("");
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([false]);
  });

  test("Clear button works when field is in error state", async () => {
    const { onErrorStateChangeSpy } = await mountDefaultStory();
    await notePage.typeInEditor("Valid note");
    await notePage.clearEditor();
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([true]);
    await notePage.clickClear();
    await expect.element(notePage.editorBody).toHaveTextContent("");
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([false]);
  });

  test("Clear button is idempotent when field is already empty", async () => {
    const { onErrorStateChangeSpy } = await mountDefaultStory();
    await expect.element(notePage.editorBody).toHaveTextContent("");
    await notePage.clickClear();
    await expect.element(notePage.editorBody).toHaveTextContent("");
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([false]);
    await notePage.clickClear();
    await expect.element(notePage.editorBody).toHaveTextContent("");
    expect(onErrorStateChangeSpy.toHaveBeenLastCalledWith()).toEqual([false]);
  });

  describe("dirty flag", () => {
    test("Manages dirty flag correctly when the subsample is in preview", async () => {
      const { dirtyFlagSpy } = await mountDefaultStory();
      await notePage.typeInEditor("test");
      expect(dirtyFlagSpy.isSet()).toBe(true);
      await notePage.clickCreateNote();
      expect(dirtyFlagSpy.isUnset()).toBe(true);
    });

    test("Does not manage dirty flag when the subsample is in editing" /*
     * When editing the whole subsample, the new note component sets the
     * dirty flag on typing but does NOT unset it after creation — another
     * field may still be dirty. This means the user may get a false unsaved-
     * changes warning if the note field was the only change, but that is an
     * accepted trade-off (see original test comment).
     */, async () => {
      const { dirtyFlagSpy } = await mountEditingStory();
      await notePage.typeInEditor("test");
      expect(dirtyFlagSpy.isSet()).toBe(true);
      await notePage.clickCreateNote();
      expect(dirtyFlagSpy.hasBeenUnset()).toBe(false);
    });
  });
});
