import React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { InventoryLink } from "@/modules/stoichiometry/schema";
import StoichiometryTableInventoryLinkCell from "@/tinyMCE/stoichiometry/StoichiometryTableInventoryLinkCell";

vi.mock("@/stores/models/Factory/MemoisedFactory", () => ({
  default: class MockMemoisedFactory {},
}));

vi.mock("@/stores/models/Search", () => ({
  default: class MockSearch {
    activeResult = null;
    alwaysFilterOut = () => false;
    uiConfig = { selectionMode: "SINGLE" as const };
    fetcher = {
      resetFetcher: vi.fn(),
      performInitialSearch: vi.fn(async () => {}),
    };
  },
}));

vi.mock("@/Inventory/components/Picker/Picker", () => ({
  default: ({
    onAddition,
    search,
  }: {
    onAddition: (records: Array<{ globalId: string }>) => void;
    search: {
      alwaysFilterOut: (record: { globalId: string }) => boolean;
    };
  }) => (
    <>
      <div>
        {search.alwaysFilterOut({ globalId: "SA123" })
          ? "SA123 filtered"
          : "SA123 available"}
      </div>
      <button
        type="button"
        onClick={() => {
          onAddition([{ globalId: "SA999" }]);
        }}
      >
        Select inventory item
      </button>
      <button
        type="button"
        onClick={() => {
          onAddition([{ globalId: "SA123" }]);
        }}
      >
        Select existing inventory item
      </button>
    </>
  ),
}));

const mockInventoryLink: InventoryLink = {
  id: 501,
  inventoryItemGlobalId: "SA123",
  stoichiometryMoleculeId: 5,
  quantity: {
    numericValue: 10,
    unitId: 20,
  },
};

describe("StoichiometryTableInventoryLinkCell", () => {
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
    await user.click(screen.getByRole("button", { name: "Select inventory item" }));

    expect(onPickInventoryItem).toHaveBeenCalledWith("SA999");
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
        linkedInventoryItemGlobalIds={["SA123"]}
      />,
    );

    await user.click(screen.getByLabelText("Add inventory link for Benzene"));
    expect(screen.getByText("SA123 filtered")).toBeVisible();
  });

  it("does not allow selecting an already linked inventory item", async () => {
    const onPickInventoryItem = vi.fn();
    const user = userEvent.setup();

    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={null}
        moleculeName="Benzene"
        linkedInventoryItemGlobalIds={["SA123"]}
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

  it("renders linked inventory chip and delete button when a link exists", () => {
    render(
      <StoichiometryTableInventoryLinkCell
        inventoryLink={mockInventoryLink}
        moleculeName="Cyclopentadiene"
      />,
    );

    expect(screen.getByText("SA123")).toBeVisible();
    expect(
      screen.getByLabelText("Remove inventory link for Cyclopentadiene"),
    ).toBeVisible();
    expect(
      screen.queryByLabelText("Add inventory link for Cyclopentadiene"),
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
      screen.getByLabelText("Remove inventory link for Cyclopentadiene"),
    ).toBeDisabled();
  });
});
