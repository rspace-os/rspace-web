import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import materialTheme from "../../../../../theme";

// The folder browser has its own tests; stub it so we can drive selection changes
// without the useFolders/API chain and assert the picker confirms via Choose.
vi.mock("../ElnFolderBrowser", () => ({
  default: ({
    onSelectionChange,
  }: {
    onSelectionChange: (s: { globalId: string; name: string; type: string } | null) => void;
  }) => (
    <div data-testid="eln-folder-browser">
      <button
        type="button"
        onClick={() =>
          onSelectionChange({
            globalId: "SD9",
            name: "Browsed doc",
            type: "DOCUMENT",
          })
        }
      >
        select doc
      </button>
      <button type="button" onClick={() => onSelectionChange(null)}>
        select folder
      </button>
    </div>
  ),
}));

import ElnRecordPicker from "../ElnRecordPicker";

function renderPicker(props: Partial<React.ComponentProps<typeof ElnRecordPicker>> = {}) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <ElnRecordPicker open onPick={vi.fn()} onCancel={vi.fn()} {...props} />
    </ThemeProvider>,
  );
}

describe("ElnRecordPicker", () => {
  afterEach(cleanup);

  it("drops straight into the folder browser with no search/browse mode choice", () => {
    renderPicker();

    // entered via "Browse ELN", so the file picker shows directly...
    expect(screen.getByTestId("eln-folder-browser")).toBeInTheDocument();
    // ...with no Search/Browse tabs or search box to choose between
    expect(screen.queryByRole("tab", { name: /search/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: /browse/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /search the eln/i })).not.toBeInTheDocument();
  });

  it("disables Choose until a target is selected", () => {
    renderPicker();
    expect(screen.getByRole("button", { name: /choose/i })).toBeDisabled();
  });

  it("confirms the selected target via Choose rather than on click, so expandable items can be browsed into", async () => {
    const onPick = vi.fn();
    const user = userEvent.setup();
    renderPicker({ onPick });

    await user.click(screen.getByRole("button", { name: /select doc/i }));
    // selection alone must not close the dialog or report a pick
    expect(onPick).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: /choose/i }));
    expect(onPick).toHaveBeenCalledWith({
      globalId: "SD9",
      name: "Browsed doc",
      type: "DOCUMENT",
    });
  });

  it("disables Choose again when the selection moves to a non-pickable item", async () => {
    const user = userEvent.setup();
    renderPicker();

    await user.click(screen.getByRole("button", { name: /select doc/i }));
    expect(screen.getByRole("button", { name: /choose/i })).toBeEnabled();

    await user.click(screen.getByRole("button", { name: /select folder/i }));
    expect(screen.getByRole("button", { name: /choose/i })).toBeDisabled();
  });

  it("forgets the previous selection when reopened", async () => {
    const onPick = vi.fn();
    const onCancel = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(
      <ThemeProvider theme={materialTheme}>
        <ElnRecordPicker open onPick={onPick} onCancel={onCancel} />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: /select doc/i }));
    rerender(
      <ThemeProvider theme={materialTheme}>
        <ElnRecordPicker open={false} onPick={onPick} onCancel={onCancel} />
      </ThemeProvider>,
    );
    rerender(
      <ThemeProvider theme={materialTheme}>
        <ElnRecordPicker open onPick={onPick} onCancel={onCancel} />
      </ThemeProvider>,
    );

    expect(screen.getByRole("button", { name: /choose/i })).toBeDisabled();
  });

  it("cancels via the Cancel button", async () => {
    const onCancel = vi.fn();
    const user = userEvent.setup();
    renderPicker({ onCancel });

    await user.click(screen.getByRole("button", { name: /cancel/i }));

    expect(onCancel).toHaveBeenCalled();
  });
});
