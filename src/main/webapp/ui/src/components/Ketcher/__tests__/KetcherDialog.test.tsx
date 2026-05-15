import React from "react";
import { screen, waitForElementToBeRemoved } from "@testing-library/react";
import { render } from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const INITIAL_KET = "initial-ket";
const mockSetMolecule = vi.fn().mockResolvedValue(undefined);
const mockGetKet = vi.fn().mockResolvedValue(INITIAL_KET);
const mockSubscribe = vi.fn();
const mockSetOptions = vi.fn();

vi.mock("ketcher-react", () => ({
  Editor: ({
    onInit,
  }: {
    onInit: (ketcher: unknown) => void;
  }) => {
    React.useEffect(() => {
      onInit({
        editor: {
          subscribe: mockSubscribe,
          setOptions: mockSetOptions,
        },
        setMolecule: mockSetMolecule,
        getKet: mockGetKet,
      });
    }, []);
    return <div data-testid="ketcher-editor" />;
  },
  InfoModal: () => null,
}));

vi.mock("ketcher-standalone", () => ({
  StandaloneStructServiceProvider: vi.fn(),
}));

import KetcherDialog from "../KetcherDialog";

describe("KetcherDialog cancel confirmation", () => {
  const handleClose = vi.fn();
  const handleInsert = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetKet.mockResolvedValue(INITIAL_KET);

    Object.defineProperty(window, "ketcher", {
      value: {
        setMolecule: mockSetMolecule,
        getKet: mockGetKet,
        editor: {
          subscribe: mockSubscribe,
          setOptions: mockSetOptions,
        },
      },
      writable: true,
      configurable: true,
    });
  });

  it("closes immediately when Cancel is clicked and editor is not dirty", async () => {
    const user = userEvent.setup();

    render(
      <KetcherDialog
        title="Test Ketcher"
        handleClose={handleClose}
        handleInsert={handleInsert}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(handleClose).toHaveBeenCalledOnce();
    expect(
      screen.queryByText("You have unsaved changes."),
    ).not.toBeInTheDocument();
  });

  it("shows confirmation dialog when Cancel is clicked and editor is dirty", async () => {
    mockGetKet.mockResolvedValueOnce(INITIAL_KET);
    mockGetKet.mockResolvedValue("changed-ket");
    const user = userEvent.setup();

    render(
      <KetcherDialog
        title="Test Ketcher"
        handleClose={handleClose}
        handleInsert={handleInsert}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(handleClose).not.toHaveBeenCalled();
    expect(
      screen.getByText(/You have unsaved changes/),
    ).toBeInTheDocument();
  });

  it("dismisses confirmation and keeps editor open when Keep Editing is clicked", async () => {
    mockGetKet.mockResolvedValueOnce(INITIAL_KET);
    mockGetKet.mockResolvedValue("changed-ket");
    const user = userEvent.setup();

    render(
      <KetcherDialog
        title="Test Ketcher"
        handleClose={handleClose}
        handleInsert={handleInsert}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await user.click(screen.getByRole("button", { name: "Keep Editing" }));

    expect(handleClose).not.toHaveBeenCalled();
    await waitForElementToBeRemoved(() =>
      screen.queryByText(/You have unsaved changes/),
    );
  });

  it("closes the editor when Discard is clicked", async () => {
    mockGetKet.mockResolvedValueOnce(INITIAL_KET);
    mockGetKet.mockResolvedValue("changed-ket");
    const user = userEvent.setup();

    render(
      <KetcherDialog
        title="Test Ketcher"
        handleClose={handleClose}
        handleInsert={handleInsert}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await user.click(screen.getByRole("button", { name: "Discard" }));

    expect(handleClose).toHaveBeenCalledOnce();
    expect(mockSetMolecule).toHaveBeenCalledWith("");
  });
});
