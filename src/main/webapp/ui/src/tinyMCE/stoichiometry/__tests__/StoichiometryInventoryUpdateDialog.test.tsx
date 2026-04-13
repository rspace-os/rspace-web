import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "@mui/material/styles";
import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import materialTheme from "@/theme";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import StoichiometryInventoryUpdateDialog from "@/tinyMCE/stoichiometry/StoichiometryInventoryUpdateDialog";

vi.mock("@/components/GlobalId", () => ({
  default: ({ record }: { record: { globalId: string } }) => <span>{record.globalId}</span>,
}));

vi.mock("@/stores/models/LinkableRecordFromGlobalId", () => ({
  default: class MockLinkableRecordFromGlobalId {
    globalId: string;

    constructor(globalId: string) {
      this.globalId = globalId;
    }
  },
}));

function makeMolecule({
  id,
  name,
  inventoryItemGlobalId,
  stockDeducted = false,
  actualAmount = 5,
}: {
  id: number;
  name: string;
  inventoryItemGlobalId: string;
  stockDeducted?: boolean;
  actualAmount?: number | null;
}): EditableMolecule {
  return {
    id,
    rsChemElement: null,
    inventoryLink: {
      id: 1000 + id,
      inventoryItemGlobalId,
      stockDeducted,
    },
    savedInventoryLink: {
      id: 1000 + id,
      inventoryItemGlobalId,
      stockDeducted,
    },
    deletedInventoryLink: null,
    role: "REACTANT",
    formula: null,
    name,
    smiles: `SMILES-${id}`,
    coefficient: 1,
    molecularWeight: 50,
    mass: 5,
    moles: null,
    actualAmount,
    actualMoles: null,
    actualYield: null,
    limitingReagent: false,
    notes: null,
  };
}

function makeQuantityMap(
  entries: Array<[string, number]>,
): ReadonlyMap<string, InventoryQuantityQueryResult> {
  return new Map(
    entries.map(([globalId, numericValue]) => [
      globalId,
      {
        status: "available",
        quantity: {
          numericValue,
          unitId: 7,
        },
      } satisfies InventoryQuantityQueryResult,
    ]),
  );
}

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <ThemeProvider theme={materialTheme}>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </ThemeProvider>,
  );
}

function getMoleculeRow(name: string): HTMLElement {
  const row = screen
    .getAllByRole("row")
    .find((candidate) => within(candidate).queryByText(name) !== null);

  if (!(row instanceof HTMLElement)) {
    throw new Error(`Molecule row not found: ${name}`);
  }

  return row;
}

function getMetric(moleculeName: string, column: string): HTMLElement {
  const metric = within(getMoleculeRow(moleculeName))
    .getAllByRole("cell")
    .find((candidate) => candidate.getAttribute("data-column") === column);

  if (!(metric instanceof HTMLElement)) {
    throw new Error(`Metric column not found: ${moleculeName} / ${column}`);
  }

  return metric;
}

describe("StoichiometryInventoryUpdateDialog", () => {
  it("submits selected molecule ids and closes when all deductions succeed", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn(() => Promise.resolve({
      results: [
        {
          moleculeId: 1,
          moleculeName: "Cyclopentane",
          success: true,
          errorMessage: null,
        },
      ],
    }));
    const onClose = vi.fn();

    renderWithProviders(
      <StoichiometryInventoryUpdateDialog
        open
        molecules={[
          makeMolecule({
            id: 1,
            name: "Cyclopentane",
            inventoryItemGlobalId: "SS124",
          }),
        ]}
        linkedInventoryQuantityInfoByGlobalId={makeQuantityMap([["SS124", 10]])}
        onSave={onSave}
        onClose={onClose}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(onSave).toHaveBeenCalledWith([1]);
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });

  it("keeps failed rows unchecked after a partial failure and refreshed stock update", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();

    function Wrapper() {
      const [molecules, setMolecules] = React.useState<ReadonlyArray<EditableMolecule>>([
        makeMolecule({
          id: 1,
          name: "Cyclopentane",
          inventoryItemGlobalId: "SS124",
        }),
        makeMolecule({
          id: 2,
          name: "Cyclopentadiene",
          inventoryItemGlobalId: "SS123",
          actualAmount: 4,
        }),
      ]);
      const [quantityMap, setQuantityMap] = React.useState<
        ReadonlyMap<string, InventoryQuantityQueryResult>
      >(makeQuantityMap([
        ["SS124", 10],
        ["SS123", 10],
      ]));

      return (
        <StoichiometryInventoryUpdateDialog
          open
          molecules={molecules}
          linkedInventoryQuantityInfoByGlobalId={quantityMap}
          onSave={() =>
            Promise.resolve().then(() => {
            setMolecules((previousMolecules) =>
              previousMolecules.map((molecule) =>
                molecule.id === 1
                  ? {
                      ...molecule,
                      inventoryLink: molecule.inventoryLink
                        ? {
                            ...molecule.inventoryLink,
                            stockDeducted: true,
                          }
                        : null,
                    }
                  : molecule,
              ),
            );
            setQuantityMap(
              makeQuantityMap([
                ["SS124", 5],
                ["SS123", 10],
              ]),
            );

            return {
              results: [
                {
                  moleculeId: 1,
                  moleculeName: "Cyclopentane",
                  success: true,
                  errorMessage: null,
                },
                {
                  moleculeId: 2,
                  moleculeName: "Cyclopentadiene",
                  success: false,
                  errorMessage: "Insufficient stock to perform this action.",
                },
              ],
            };
            })}
          onClose={onClose}
        />
      );
    }

    renderWithProviders(<Wrapper />);

    expect(screen.getByRole("checkbox", { name: "Cyclopentane" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Cyclopentadiene" })).toBeChecked();

    await user.click(screen.getByRole("button", { name: "Save" }));

    await screen.findByText(
      /Current stock amounts were refreshed\. Re-select any remaining molecules to retry\./i,
    );

    await waitFor(() => {
      expect(screen.getByRole("checkbox", { name: "Cyclopentane" })).toBeEnabled();
      expect(screen.getByRole("checkbox", { name: "Cyclopentane" })).not.toBeChecked();
      expect(screen.getByRole("checkbox", { name: "Cyclopentadiene" })).toBeEnabled();
      expect(screen.getByRole("checkbox", { name: "Cyclopentadiene" })).not.toBeChecked();
    });
    expect(
      within(getMetric("Cyclopentane", "In Stock")).getByText("5.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Cyclopentane", "Will Use")).getByText("5.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Cyclopentane", "Remaining")).getByText("0.0 g"),
    ).toBeVisible();
    expect(
      screen.getByText("Insufficient stock to perform this action."),
    ).toBeVisible();
    expect(
      screen.getByText(
        "Stock has already been deducted for this molecule. To reduce the stock again, select this molecule.",
      ),
    ).toBeVisible();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("shows a warning for already deducted molecules without auto-selecting them", () => {
    renderWithProviders(
      <StoichiometryInventoryUpdateDialog
        open
        molecules={[
          makeMolecule({
            id: 1,
            name: "Cyclopentane",
            inventoryItemGlobalId: "SS124",
            stockDeducted: true,
          }),
        ]}
        linkedInventoryQuantityInfoByGlobalId={makeQuantityMap([["SS124", 10]])}
        onClose={() => {}}
      />,
    );

    expect(screen.getByRole("checkbox", { name: "Cyclopentane" })).toBeEnabled();
    expect(
      screen.getByRole("checkbox", { name: "Cyclopentane" }),
    ).not.toBeChecked();
    expect(
      screen.getByText(
        "Stock has already been deducted for this molecule. To reduce the stock again, select this molecule.",
      ),
    ).toBeVisible();
  });

  it("disables Save when a selected molecule becomes invalid after refresh", async () => {
    const user = userEvent.setup();

    function Wrapper() {
      const [quantityMap, setQuantityMap] = React.useState<
        ReadonlyMap<string, InventoryQuantityQueryResult>
      >(makeQuantityMap([["SS124", 10]]));

      return (
        <>
          <button
            type="button"
            onClick={() => {
              setQuantityMap(makeQuantityMap([["SS124", 2]]));
            }}
          >
            Invalidate selection
          </button>
          <StoichiometryInventoryUpdateDialog
            open
            molecules={[
              makeMolecule({
                id: 1,
                name: "Cyclopentane",
                inventoryItemGlobalId: "SS124",
              }),
            ]}
            linkedInventoryQuantityInfoByGlobalId={quantityMap}
            onSave={() => Promise.resolve({ results: [] })}
            onClose={() => {}}
          />
        </>
      );
    }

    renderWithProviders(<Wrapper />);

    const saveButton = screen.getByRole("button", { name: "Save" });
    expect(saveButton).toBeEnabled();

    await user.click(screen.getByText("Invalidate selection"));

    await waitFor(() => {
      expect(screen.getByRole("checkbox", { name: "Cyclopentane" })).toBeDisabled();
      expect(saveButton).toBeDisabled();
    });
    expect(
      screen.getByText("Re-select any invalid molecules before saving."),
    ).toBeVisible();
  });

  it("shows a local error message and clears selection when saving fails", async () => {
    const user = userEvent.setup();
    const onSave = vi.fn(() => Promise.reject(new Error("Network down")));

    renderWithProviders(
      <StoichiometryInventoryUpdateDialog
        open
        molecules={[
          makeMolecule({
            id: 1,
            name: "Cyclopentane",
            inventoryItemGlobalId: "SS124",
          }),
        ]}
        linkedInventoryQuantityInfoByGlobalId={makeQuantityMap([["SS124", 10]])}
        onSave={onSave}
        onClose={() => {}}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Save" }));

    await screen.findByText(
      "Network down Current stock amounts were refreshed where possible.",
    );
    expect(screen.getByRole("checkbox", { name: "Cyclopentane" })).not.toBeChecked();
  });
});
