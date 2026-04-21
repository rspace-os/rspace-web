import React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { InventoryLink } from "@/modules/stoichiometry/schema";
import StoichiometryTableInventoryLinkCell from "@/tinyMCE/stoichiometry/StoichiometryTableInventoryLinkCell";

vi.mock("@/Inventory/components/RecordLink", () => ({
  RecordLink: ({
    record,
  }: {
    record: { recordLinkLabel: string; permalinkURL: string | null };
  }) => (
    <a href={record.permalinkURL ?? undefined}>{record.recordLinkLabel}</a>
  ),
}));

vi.mock("@/stores/models/Factory/MemoisedFactory", () => ({
  default: class MockMemoisedFactory {},
}));

vi.mock("@/stores/models/Search", () => ({
  default: class MockSearch {
    activeResult = null;
    uiConfig = { selectionMode: "SINGLE" as const };
    fetcher = {
      resetFetcher: vi.fn(),
      performInitialSearch: vi.fn(async () => {}),
    };

    alwaysFilterOut = () => false;
  },
}));

vi.mock("@/Inventory/components/Picker/Picker", () => ({
  default: ({
    onAddition,
    resetActiveResultOnClose,
    search,
  }: {
    onAddition: (records: Array<{ id: number; globalId: string }>) => void;
    resetActiveResultOnClose?: boolean;
    search: {
      alwaysFilterOut: (record: { globalId: string }) => boolean;
    };
  }) => (
    <>
      <div>
        {resetActiveResultOnClose
          ? "reset active result enabled"
          : "reset active result disabled"}
      </div>
      <div>
        {search.alwaysFilterOut({ globalId: "SS123" })
          ? "SS123 filtered"
          : "SS123 available"}
      </div>
      <button
        type="button"
        onClick={() => {
          onAddition([{ id: 999, globalId: "SS999" }]);
        }}
      >
        Select inventory item
      </button>
      <button
        type="button"
        onClick={() => {
          onAddition([{ id: 123, globalId: "SS123" }]);
        }}
      >
        Select existing inventory item
      </button>
    </>
  ),
}));

const mockInventoryLink: InventoryLink = {
  id: 501,
  inventoryItemGlobalId: "SS123",
  stockDeducted: false,
  stoichiometryMoleculeId: 5,
  quantity: {
    numericValue: 10,
    unitId: 20,
  },
};

describe("StoichiometryTableInventoryLinkCell", () => {
  it("should have no sa11y violations when the molecule has no inventory link", async () => {
    const { baseElement } = render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        moleculeName="Benzene"
      />,
    );

    await (
      expect(baseElement) as unknown as { toBeAccessible: () => Promise<void> }
    ).toBeAccessible();
  });

  it("should have no sa11y violations when the molecule has an inventory link", async () => {
    const { baseElement } = render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={mockInventoryLink}
        moleculeName="Cyclopentadiene"
      />,
    );

    await (
      expect(baseElement) as unknown as { toBeAccessible: () => Promise<void> }
    ).toBeAccessible();
  });

  it("renders an add button when the molecule has no inventory link", () => {
    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        moleculeName="Benzene"
      />,
    );

    expect(
      screen.getByLabelText("Add inventory link for Benzene"),
    ).toBeVisible();
    expect(
      screen.queryByLabelText("Remove inventory link for Benzene"),
    ).not.toBeInTheDocument();
  });

  it("opens InventoryPicker and returns selected inventory item id", async () => {
    const onPickInventoryItem = vi.fn();
    const user = userEvent.setup();

    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        moleculeName="Benzene"
        onPickInventoryItem={onPickInventoryItem}
      />,
    );

    await user.click(screen.getByLabelText("Add inventory link for Benzene"));

    expect(
      screen.getByRole("dialog", {
        name: "Pick inventory item for Benzene",
      }),
    ).toBeVisible();
    expect(screen.getByText("reset active result enabled")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Select inventory item" }));

    expect(onPickInventoryItem).toHaveBeenCalledWith(999, "SS999");
    await waitFor(() => {
      expect(
        screen.getByRole("dialog", {
          name: "Pick inventory item for Benzene",
        }),
      ).not.toBeVisible();
    });
  });

  it("filters out already linked inventory items in InventoryPicker", async () => {
    const user = userEvent.setup();

    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        moleculeName="Benzene"
        linkedInventoryItemGlobalIds={["SS123"]}
      />,
    );

    await user.click(screen.getByLabelText("Add inventory link for Benzene"));
    expect(screen.getByText("SS123 filtered")).toBeVisible();
  });

  it("does not allow selecting an already linked inventory item", async () => {
    const onPickInventoryItem = vi.fn();
    const user = userEvent.setup();

    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        moleculeName="Benzene"
        linkedInventoryItemGlobalIds={["SS123"]}
        onPickInventoryItem={onPickInventoryItem}
      />,
    );

    await user.click(screen.getByLabelText("Add inventory link for Benzene"));
    await user.click(
      screen.getByRole("button", { name: "Select existing inventory item" }),
    );

    expect(onPickInventoryItem).not.toHaveBeenCalled();
    expect(
      screen.getByRole("dialog", {
        name: "Pick inventory item for Benzene",
      }),
    ).toBeVisible();
  });

  it("renders linked inventory record link and delete button when a link exists", () => {
    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={mockInventoryLink}
        moleculeName="Cyclopentadiene"
      />,
    );

    expect(screen.getByRole("link", { name: "SS123" })).toHaveAttribute(
      "href",
      "/inventory/subsample/123",
    );
    expect(
      screen.getByLabelText("Remove inventory link for Cyclopentadiene"),
    ).toBeVisible();
    expect(
      screen.queryByLabelText("Add inventory link for Cyclopentadiene"),
    ).not.toBeInTheDocument();
  });

  it("renders a deleted placeholder with undo action for a soft-deleted saved link", async () => {
    const onUndoRemoveInventoryLink = vi.fn();
    const user = userEvent.setup();

    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        isDeleted
        moleculeName="Cyclopentadiene"
        onUndoRemoveInventoryLink={onUndoRemoveInventoryLink}
      />,
    );

    expect(screen.getByText("Link Deleted")).toBeVisible();
    expect(
      screen.getByLabelText("Undo deleting inventory link for Cyclopentadiene"),
    ).toBeVisible();
    expect(
      screen.queryByLabelText("Add inventory link for Cyclopentadiene"),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByLabelText("Undo deleting inventory link for Cyclopentadiene"),
    );

    expect(onUndoRemoveInventoryLink).toHaveBeenCalledTimes(1);
  });

  it("does not show the add button while a saved link deletion is pending", () => {
    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        isDeleted
        moleculeName="Cyclopentadiene"
      />,
    );

    expect(screen.getByText("Link Deleted")).toBeVisible();
    expect(
      screen.queryByLabelText("Add inventory link for Cyclopentadiene"),
    ).not.toBeInTheDocument();
  });

  it("renders an insufficient stock warning when requested", () => {
    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={mockInventoryLink}
        moleculeName="Cyclopentadiene"
        showInsufficientStockWarning
      />,
    );

    expect(
      screen.getByLabelText("Insufficient Stock", { selector: "svg" }),
    ).toBeVisible();
  });

  it("renders a stock deducted indicator and hides the insufficient stock warning when stock was already deducted", () => {
    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={{ ...mockInventoryLink, stockDeducted: true }}
        moleculeName="Cyclopentadiene"
        showInsufficientStockWarning
      />,
    );

    expect(screen.getByLabelText("Stock deducted")).toBeVisible();
    expect(
      screen.queryByLabelText("Insufficient Stock", { selector: "svg" }),
    ).not.toBeInTheDocument();
  });

  it("does not render an insufficient stock warning by default", () => {
    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={mockInventoryLink}
        moleculeName="Cyclopentadiene"
      />,
    );

    expect(
      screen.queryByLabelText("Insufficient Stock", { selector: "svg" }),
    ).not.toBeInTheDocument();
  });

  it("disables controls in read-only mode", () => {
    const { rerender } = render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        moleculeName="Benzene"
        editable={false}
      />,
    );

    expect(
      screen.getByLabelText("Add inventory link for Benzene"),
    ).toBeDisabled();

    rerender(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={mockInventoryLink}
        moleculeName="Cyclopentadiene"
        editable={false}
      />,
    );
    expect(
      screen.queryByLabelText("Remove inventory link for Cyclopentadiene"),
    ).not.toBeInTheDocument();

    rerender(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        isDeleted
        moleculeName="Cyclopentadiene"
        editable={false}
      />,
    );
    expect(
      screen.queryByLabelText("Undo deleting inventory link for Cyclopentadiene"),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Link Deleted")).toBeVisible();
  });
});
