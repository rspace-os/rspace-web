import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StoichiometryDialog from "@/tinyMCE/stoichiometry/StoichiometryDialog";

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
    selectLimitingReagent: ReturnType<typeof vi.fn>;
    processRowUpdate: ReturnType<typeof vi.fn>;
  };
};

const mockUpdateInventoryStock = vi.fn();
const mockUseEditableStoichiometryTable = vi.fn();

vi.mock("@/components/DialogBoundary", () => ({
  Dialog: ({
    open,
    children,
    "aria-labelledby": ariaLabelledBy,
  }: {
    open: boolean;
    children: React.ReactNode;
    "aria-labelledby"?: string;
  }) =>
    open ? (
      <div role="dialog" aria-labelledby={ariaLabelledBy}>
        {children}
      </div>
    ) : null,
  DialogBoundary: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock("@/hooks/api/integrationHelpers", () => ({
  useIntegrationIsAllowedAndEnabled: () => ({ tag: "success", value: true }),
}));

vi.mock("@/hooks/auth/useOauthToken", () => ({
  default: () => ({ getToken: vi.fn() }),
}));

vi.mock("@/modules/stoichiometry/mutations", () => ({
  useCalculateStoichiometryMutation: () => ({
    mutate: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
}));

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

vi.mock("../../../components/AppBar", () => ({
  default: () => null,
}));

vi.mock("../../../components/ConfirmProvider", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock("@/components/ConfirmProvider", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
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

describe("StoichiometryDialog", () => {
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

    render(
      <StoichiometryDialog
        open
        onClose={() => {}}
        chemId={12345}
        recordId={1}
        stoichiometryId={1}
        stoichiometryRevision={1}
        onSave={onSave}
      />,
    );

    await user.click(
      await screen.findByRole("button", { name: "Trigger Inventory Save" }),
    );

    await waitFor(() => {
      expect(mockUpdateInventoryStock).toHaveBeenCalledWith([1]);
      expect(onSave).toHaveBeenCalledWith(1, 2);
    });
  });
});




