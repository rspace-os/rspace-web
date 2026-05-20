import { describe, expect, test, vi } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { render, screen, waitFor } from "@/__tests__/customQueries";
import { NewNoteStory } from "../NewNote.story";
import "@/__tests__/__mocks__/matchMedia";

type CallbackSpy = {
  handler: (...args: unknown[]) => void;
  calls: unknown[][];
};

function createCallbackSpy(): CallbackSpy {
  const calls: unknown[][] = [];
  return {
    handler: (...args: unknown[]) => {
      calls.push(args);
    },
    calls,
  };
}

function createDirtyFlagSpy() {
  let value = false;
  let setCalls = 0;
  let unsetCalls = 0;
  return {
    set: () => {
      value = true;
      setCalls += 1;
    },
    unset: () => {
      value = false;
      unsetCalls += 1;
    },
    isSet: () => value,
    setCalls: () => setCalls,
    unsetCalls: () => unsetCalls,
  };
}

describe("NewNote", () => {
  test("is accessible", async () => {
    const { baseElement } = render(<NewNoteStory />);

    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(baseElement).toBeAccessible();
  });

  test("shows an error for an empty note on first render", () => {
    render(<NewNoteStory />);

    expect(screen.getByText("Note cannot be empty.")).toBeVisible();
  });

  test("creates a valid note and clears the field", async () => {
    const user = userEvent.setup();
    const onErrorStateChangeSpy = createCallbackSpy();
    const createNote = vi.fn().mockResolvedValue(undefined);

    render(
      <NewNoteStory
        onErrorStateChange={onErrorStateChangeSpy.handler}
        createNote={createNote}
      />,
    );

    const textbox = screen.getByRole("textbox", { name: /new note/i });
    await user.clear(textbox);
    await user.type(textbox, "This is a valid note");
    await user.click(screen.getByRole("button", { name: /create note/i }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalledWith({ content: "This is a valid note" });
    });
    await waitFor(() => {
      expect(textbox).toHaveValue("");
    });
    expect(onErrorStateChangeSpy.calls).toContainEqual([false]);
  });

  test("transitions from valid to invalid after removing all text", async () => {
    const user = userEvent.setup();
    const onErrorStateChangeSpy = createCallbackSpy();

    render(<NewNoteStory onErrorStateChange={onErrorStateChangeSpy.handler} />);

    const textbox = screen.getByRole("textbox", { name: /new note/i });
    await user.clear(textbox);
    await user.type(textbox, "Valid note");
    await user.clear(textbox);

    expect(screen.getByText("Note cannot be empty.")).toBeVisible();
    expect(onErrorStateChangeSpy.calls).toContainEqual([true]);
  });

  test("clear button clears the field and resets the error state", async () => {
    const user = userEvent.setup();
    const onErrorStateChangeSpy = createCallbackSpy();

    render(<NewNoteStory onErrorStateChange={onErrorStateChangeSpy.handler} />);

    const textbox = screen.getByRole("textbox", { name: /new note/i });
    await user.clear(textbox);
    await user.type(textbox, "Test note");
    await user.click(screen.getByRole("button", { name: /clear/i }));

    expect(textbox).toHaveValue("");
    expect(onErrorStateChangeSpy.calls.at(-1)).toEqual([false]);
  });

  test("shows a non-editable message when notes are not editable", () => {
    render(<NewNoteStory isEditable={false} />);

    expect(screen.getByText("Notes are not editable")).toBeVisible();
    expect(screen.getByRole("button", { name: /create note/i })).toBeDisabled();
  });

  test("sets and unsets the dirty flag in preview mode", async () => {
    const user = userEvent.setup();
    const dirtyFlagSpy = createDirtyFlagSpy();
    const createNote = vi.fn().mockResolvedValue(undefined);

    render(
      <NewNoteStory
        createNote={createNote}
        setDirtyFlag={dirtyFlagSpy.set}
        unsetDirtyFlag={dirtyFlagSpy.unset}
      />,
    );

    const textbox = screen.getByRole("textbox", { name: /new note/i });
    await user.clear(textbox);
    await user.type(textbox, "Dirty note");
    expect(dirtyFlagSpy.isSet()).toBe(true);

    await user.click(screen.getByRole("button", { name: /create note/i }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalled();
    });
    expect(dirtyFlagSpy.unsetCalls()).toBeGreaterThan(0);
  });
});
