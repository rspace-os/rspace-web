import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render } from "@/__tests__/customQueries";

const INITIAL_KET = "initial-ket";
const mockSetMolecule = vi.fn().mockResolvedValue(undefined);
const mockGetKet = vi.fn().mockResolvedValue(INITIAL_KET);
const mockSubscribe = vi.fn();
const mockSetOptions = vi.fn();

vi.mock("ketcher-react", () => ({
  Editor: ({ onInit }: { onInit: (ketcher: unknown) => void }) => {
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

    render(<KetcherDialog title="Test Ketcher" handleClose={handleClose} handleInsert={handleInsert} />);

    await user.click(screen.getByRole("button", { name: "actions.cancel" }));

    expect(handleClose).toHaveBeenCalledOnce();
    expect(screen.queryByText("ketcher.discardChangesDialog.text")).not.toBeInTheDocument();
  });

  it("shows confirmation dialog when Cancel is clicked and editor is dirty", async () => {
    mockGetKet.mockResolvedValueOnce(INITIAL_KET);
    mockGetKet.mockResolvedValue("changed-ket");
    const user = userEvent.setup();

    render(<KetcherDialog title="Test Ketcher" handleClose={handleClose} handleInsert={handleInsert} />);

    await user.click(screen.getByRole("button", { name: "actions.cancel" }));

    expect(handleClose).not.toHaveBeenCalled();
    expect(screen.getByText("ketcher.discardChangesDialog.text")).toBeInTheDocument();
  });

  it("dismisses confirmation and keeps editor open when Keep Editing is clicked", async () => {
    mockGetKet.mockResolvedValueOnce(INITIAL_KET);
    mockGetKet.mockResolvedValue("changed-ket");
    const user = userEvent.setup();

    render(<KetcherDialog title="Test Ketcher" handleClose={handleClose} handleInsert={handleInsert} />);

    await user.click(screen.getByRole("button", { name: "actions.cancel" }));
    await user.click(screen.getByRole("button", { name: "ketcher.discardChangesDialog.keepEditing" }));

    expect(handleClose).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByText("ketcher.discardChangesDialog.text")).not.toBeInTheDocument());
  });

  it("skips dirty check and closes immediately in read-only mode", async () => {
    mockGetKet.mockResolvedValueOnce(INITIAL_KET);
    mockGetKet.mockResolvedValue("changed-ket");
    const user = userEvent.setup();

    render(<KetcherDialog title="Test Ketcher" handleClose={handleClose} handleInsert={handleInsert} readOnly />);

    await user.click(screen.getByRole("button", { name: "actions.cancel" }));

    expect(handleClose).toHaveBeenCalledOnce();
    expect(screen.queryByText("ketcher.discardChangesDialog.text")).not.toBeInTheDocument();
  });

  it("closes the editor when Discard is clicked", async () => {
    mockGetKet.mockResolvedValueOnce(INITIAL_KET);
    mockGetKet.mockResolvedValue("changed-ket");
    const user = userEvent.setup();

    render(<KetcherDialog title="Test Ketcher" handleClose={handleClose} handleInsert={handleInsert} />);

    await user.click(screen.getByRole("button", { name: "actions.cancel" }));
    await user.click(screen.getByRole("button", { name: "ketcher.discardChangesDialog.discard" }));

    expect(handleClose).toHaveBeenCalledOnce();
    expect(mockSetMolecule).toHaveBeenCalledWith("");
  });
});
