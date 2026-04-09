import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EditableStoichiometryDialogSection from "@/tinyMCE/stoichiometry/dialog/EditableStoichiometryDialogSection";

type MockEditableStoichiometryTableResult = {
  hasChanges: boolean;
  isBusy: boolean;
  isSaving: boolean;
  save: ReturnType<typeof vi.fn>;
  deleteTable: ReturnType<typeof vi.fn>;
  tableController: {
    allMolecules: [];
    linkedInventoryQuantityInfoByGlobalId: Map<string, never>;
    isGettingMoleculeInfo: boolean;
    addReagent: ReturnType<typeof vi.fn>;
    deleteReagent: ReturnType<typeof vi.fn>;
    updateInventoryStock: typeof mockUpdateInventoryStock;
    pickInventoryLink: ReturnType<typeof vi.fn>;
    removeInventoryLink: ReturnType<typeof vi.fn>;
    undoRemoveInventoryLink: ReturnType<typeof vi.fn>;
    selectLimitingReagent: ReturnType<typeof vi.fn>;
    processRowUpdate: ReturnType<typeof vi.fn>;
  };
};

const mockUpdateInventoryStock = vi.fn();
const mockUseEditableStoichiometryTable = vi.fn();

vi.mock("@/tinyMCE/stoichiometry/useEditableStoichiometryTable", () => ({
  useEditableStoichiometryTable: ({
    stoichiometryId,
    stoichiometryRevision,
  }: {
    stoichiometryId: number;
    stoichiometryRevision: number;
  }): MockEditableStoichiometryTableResult =>
    mockUseEditableStoichiometryTable({
      stoichiometryId,
      stoichiometryRevision,
    }) as MockEditableStoichiometryTableResult,
}));

vi.mock("@/components/ConfirmProvider", () => ({
  useConfirm: () => vi.fn(() => Promise.resolve(true)),
}));

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTable", async () => {
  const { useStoichiometryTableController } = await import(
    "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext"
  );

  return {
    default: function MockStoichiometryTable() {
      const tableController = useStoichiometryTableController();

      return (
        <button
          type="button"
          onClick={() => {
            void tableController?.updateInventoryStock([1]);
          }}
        >
          Trigger Inventory Save
        </button>
      );
    },
  };
});

describe("EditableStoichiometryDialogSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockUpdateInventoryStock.mockResolvedValue({
      refreshedStoichiometry: {
        id: 1,
        revision: 2,
      },
      results: [],
    });

    mockUseEditableStoichiometryTable.mockReturnValue({
      hasChanges: false,
      isBusy: false,
      isSaving: false,
      save: vi.fn(),
      deleteTable: vi.fn(),
      tableController: {
        allMolecules: [],
        linkedInventoryQuantityInfoByGlobalId: new Map(),
        isGettingMoleculeInfo: false,
        addReagent: vi.fn(async () => {}),
        deleteReagent: vi.fn(),
        updateInventoryStock: mockUpdateInventoryStock,
        pickInventoryLink: vi.fn(),
        removeInventoryLink: vi.fn(),
        undoRemoveInventoryLink: vi.fn(),
        selectLimitingReagent: vi.fn(),
        processRowUpdate: vi.fn(),
      },
    });
  });

  it("calls onSave when inventory stock update refreshes stoichiometry", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn();
    const setCurrentStoichiometry = vi.fn();

    render(
      <EditableStoichiometryDialogSection
        currentStoichiometry={{ id: 1, revision: 1 }}
        onClose={() => {}}
        onSave={onSave}
        onDelete={() => {}}
        setCurrentStoichiometry={setCurrentStoichiometry}
      />,
    );

    await user.click(
      await screen.findByRole("button", { name: "Trigger Inventory Save" }),
    );

    await waitFor(() => {
      expect(mockUpdateInventoryStock).toHaveBeenCalledWith([1]);
      expect(setCurrentStoichiometry).toHaveBeenCalledWith({ id: 1, revision: 2 });
      expect(onSave).toHaveBeenCalledWith(1, 2);
    });
  });
});
