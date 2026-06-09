import { afterEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

// The folder browser has its own tests; stub it so we can assert the picker delegates to it
// and forwards the chosen target, without the useFolders/API chain.
vi.mock("../ElnFolderBrowser", () => ({
  default: ({
    onPick,
  }: {
    onPick: (s: { globalId: string; name: string; type: string }) => void;
  }) => (
    <button
      type="button"
      data-testid="eln-folder-browser-pick"
      onClick={() =>
        onPick({ globalId: "SD9", name: "Browsed doc", type: "DOCUMENT" })
      }
    >
      browse pick
    </button>
  ),
}));

import ElnRecordPicker from "../ElnRecordPicker";

function renderPicker(
  props: Partial<React.ComponentProps<typeof ElnRecordPicker>> = {},
) {
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
    expect(screen.getByTestId("eln-folder-browser-pick")).toBeInTheDocument();
    // ...with no Search/Browse tabs or search box to choose between
    expect(
      screen.queryByRole("tab", { name: /search/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("tab", { name: /browse/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("textbox", { name: /search the eln/i }),
    ).not.toBeInTheDocument();
  });

  it("returns the chosen target via onPick", async () => {
    const onPick = vi.fn();
    const user = userEvent.setup();
    renderPicker({ onPick });

    await user.click(screen.getByTestId("eln-folder-browser-pick"));

    expect(onPick).toHaveBeenCalledWith({
      globalId: "SD9",
      name: "Browsed doc",
      type: "DOCUMENT",
    });
  });

  it("cancels via the Cancel button", async () => {
    const onCancel = vi.fn();
    const user = userEvent.setup();
    renderPicker({ onCancel });

    await user.click(screen.getByRole("button", { name: /cancel/i }));

    expect(onCancel).toHaveBeenCalled();
  });
});
