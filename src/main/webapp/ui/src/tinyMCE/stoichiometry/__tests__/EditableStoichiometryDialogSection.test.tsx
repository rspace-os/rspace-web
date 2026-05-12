import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import EditableStoichiometryDialogSection from "@/tinyMCE/stoichiometry/dialog/EditableStoichiometryDialogSection";
import type { RefreshedStoichiometry } from "@/tinyMCE/stoichiometry/useEditableStoichiometryTable";

type MockEditableStoichiometryTableResult = {
  hasChanges: boolean;
  isBusy: boolean;
  isSaving: boolean;
  save: ReturnType<typeof vi.fn>;
  deleteTable: ReturnType<typeof vi.fn>;
  tableController: {
    allMolecules: [];
    linkedInventoryQuantityInfoByGlobalId: Map<string, unknown>;
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

type MockUseEditableStoichiometryTableArgs = {
  stoichiometryId: number;
  stoichiometryRevision: number;
  onStoichiometryRefreshed?: (stoichiometry: RefreshedStoichiometry) => void;
};

const mockUpdateInventoryStock = vi.fn();
const mockUseEditableStoichiometryTable = vi.fn<
  (args: MockUseEditableStoichiometryTableArgs) => MockEditableStoichiometryTableResult
>();

function createConsoleErrorSpy() {
  return vi.spyOn(console, "error").mockImplementation(() => {});
}

let consoleErrorSpy: ReturnType<typeof createConsoleErrorSpy>;

vi.mock("@/tinyMCE/stoichiometry/useEditableStoichiometryTable", () => ({
  useEditableStoichiometryTable: ({
    stoichiometryId,
    stoichiometryRevision,
    onStoichiometryRefreshed,
  }: MockUseEditableStoichiometryTableArgs): MockEditableStoichiometryTableResult =>
    mockUseEditableStoichiometryTable({
      stoichiometryId,
      stoichiometryRevision,
      onStoichiometryRefreshed,
    }),
}));

vi.mock("@/components/ConfirmProvider", () => ({
  useConfirm: () => vi.fn(() => Promise.resolve(true)),
}));

vi.mock("@/components/ValidatingSubmitButton", () => ({
  __esModule: true,
  default: ({
    children,
    onClick,
    loading,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
    loading?: boolean;
  }) => (
    <button type="button" onClick={onClick} disabled={loading}>
      {children}
    </button>
  ),
  IsValid: () => ({ isValid: true }),
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
    consoleErrorSpy = createConsoleErrorSpy();

    mockUseEditableStoichiometryTable.mockImplementation(
      ({ onStoichiometryRefreshed }: MockUseEditableStoichiometryTableArgs) => {
        mockUpdateInventoryStock.mockImplementation(() => {
          const refreshedStoichiometry = {
            id: 1,
            revision: 2,
          };

          onStoichiometryRefreshed?.(refreshedStoichiometry);

          return Promise.resolve({
            refreshedStoichiometry,
            results: [],
          });
        });

        return {
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
        };
      },
    );
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it("passes a stoichiometry refresh callback to the hook", () => {
    render(
      <EditableStoichiometryDialogSection
        currentStoichiometry={{ id: 1, revision: 1 }}
        chemId={null}
        onClose={() => {}}
        onDelete={() => {}}
        setCurrentStoichiometry={vi.fn()}
      />,
    );

    const firstCallArgs = mockUseEditableStoichiometryTable.mock.calls[0]?.[0];

    expect(firstCallArgs?.stoichiometryId).toBe(1);
    expect(firstCallArgs?.stoichiometryRevision).toBe(1);
    expect(typeof firstCallArgs?.onStoichiometryRefreshed).toBe("function");
  });

  it("calls onSave when inventory stock update refreshes stoichiometry", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn();
    const setCurrentStoichiometry = vi.fn();

    render(
      <EditableStoichiometryDialogSection
        currentStoichiometry={{ id: 1, revision: 1 }}
        chemId={null}
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

  it("shows an inline error alert when save fails", async () => {
    const user = userEvent.setup();
    const saveError = new Error("Save mutation failed");

    mockUseEditableStoichiometryTable.mockImplementation(
      ({ onStoichiometryRefreshed }: MockUseEditableStoichiometryTableArgs) => {
        mockUpdateInventoryStock.mockImplementation(() => {
          const refreshedStoichiometry = {
            id: 1,
            revision: 2,
          };

          onStoichiometryRefreshed?.(refreshedStoichiometry);

          return Promise.resolve({
            refreshedStoichiometry,
            results: [],
          });
        });

        return {
          hasChanges: true,
          isBusy: false,
          isSaving: false,
          save: vi.fn().mockRejectedValue(saveError),
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
        };
      },
    );

    render(
      <EditableStoichiometryDialogSection
        currentStoichiometry={{ id: 1, revision: 1 }}
        chemId={null}
        onClose={() => {}}
        onDelete={() => {}}
        setCurrentStoichiometry={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Save Changes" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Save mutation failed",
    );
  });

  it("shows an inline error alert when delete fails", async () => {
    const user = userEvent.setup();
    const deleteError = new Error("Delete mutation failed");

    mockUseEditableStoichiometryTable.mockImplementation(
      ({ onStoichiometryRefreshed }: MockUseEditableStoichiometryTableArgs) => {
        mockUpdateInventoryStock.mockImplementation(() => {
          const refreshedStoichiometry = {
            id: 1,
            revision: 2,
          };

          onStoichiometryRefreshed?.(refreshedStoichiometry);

          return Promise.resolve({
            refreshedStoichiometry,
            results: [],
          });
        });

        return {
          hasChanges: false,
          isBusy: false,
          isSaving: false,
          save: vi.fn(),
          deleteTable: vi.fn().mockRejectedValue(deleteError),
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
        };
      },
    );

    render(
      <EditableStoichiometryDialogSection
        currentStoichiometry={{ id: 1, revision: 1 }}
        chemId={null}
        onClose={() => {}}
        onDelete={() => {}}
        setCurrentStoichiometry={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Delete mutation failed",
    );
  });
});
