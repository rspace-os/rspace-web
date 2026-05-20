import { describe, expect, test, vi } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { render, screen, waitFor } from "@/__tests__/customQueries";
import { NewNoteStory } from "../NewNote.story";

vi.mock("@/components/Inputs/StyledTinyMceEditor", () => ({
  __esModule: true,
  default: ({ value, onEditorChange }: {
    value?: string;
    onEditorChange?: (content: string) => void;
  }) => (
    <textarea
      aria-label="New note editor"
      value={value ?? ""}
      onChange={(event) => {
        onEditorChange?.(event.target.value);
      }}
    />
  ),
}));

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
  let unsetCalls = 0;
  return {
    set: () => {
      value = true;
    },
    unset: () => {
      value = false;
      unsetCalls += 1;
    },
    isSet: () => value,
    unsetCalls: () => unsetCalls,
  };
}

describe("NewNote", () => {
  test("is accessible", async () => {
    const { baseElement } = render(<NewNoteStory />);

    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
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

    const textbox = screen.getByRole("textbox", { name: /new note editor/i });
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

    const textbox = screen.getByRole("textbox", { name: /new note editor/i });
    await user.type(textbox, "Valid note");
    await user.clear(textbox);

    expect(onErrorStateChangeSpy.calls).toContainEqual([true]);
  });

  test("clear button clears the field and resets the error state", async () => {
    const user = userEvent.setup();
    const onErrorStateChangeSpy = createCallbackSpy();

    render(<NewNoteStory onErrorStateChange={onErrorStateChangeSpy.handler} />);

    const textbox = screen.getByRole("textbox", { name: /new note editor/i });
    await user.type(textbox, "Test note");
    await user.click(screen.getByRole("button", { name: /clear/i }));

    expect(textbox).toHaveValue("");
    expect(onErrorStateChangeSpy.calls.at(-1)).toEqual([false]);
  });

  test("does not create a note when notes are not editable", async () => {
    const user = userEvent.setup();
    const createNote = vi.fn().mockResolvedValue(undefined);

    render(<NewNoteStory isEditable={false} createNote={createNote} />);

    await user.click(screen.getByRole("button", { name: /create note/i }));

    expect(createNote).not.toHaveBeenCalled();
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

    const textbox = screen.getByRole("textbox", { name: /new note editor/i });
    await user.type(textbox, "Dirty note");
    expect(dirtyFlagSpy.isSet()).toBe(true);

    await user.click(screen.getByRole("button", { name: /create note/i }));

    await waitFor(() => {
      expect(createNote).toHaveBeenCalled();
    });
    expect(dirtyFlagSpy.unsetCalls()).toBeGreaterThan(0);
  });
});
