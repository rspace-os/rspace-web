import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/matchMedia";
// The inventory picker's Searchbar uses ResizeObserver via useIsTextWiderThanField.
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/resizeObserver";
// useOauthToken: needed by the static read-only table and the editable hook.
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/useOauthToken";
import MockAdapter from "axios-mock-adapter";
import * as Jwt from "jsonwebtoken";
import { HttpResponse, http } from "msw";
import { uiNavigationData } from "@/__tests__/helpers/appChrome";
import { server } from "@/__tests__/mswServer";
import axios from "@/common/axios";
import StoichiometryTable from "@/tinyMCE/stoichiometry/StoichiometryTable";
import {
  type StoichiometryTableController,
  StoichiometryTableControllerProvider,
} from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import { StoichiometryTableWithDataStory } from "./StoichiometryTable.story";

/*
 * The data story renders the full app chrome (AppBar -> HelpDocs), which makes
 * axios calls (e.g. livechatProperties, uiNavigationData) that are not part of
 * what these tests assert. Without a stub they reject with "Network Error" and
 * surface as unhandled rejections that fail the run. A catch-all axios adapter
 * resolves them; fetch-based requests are handled separately by routeNetwork().
 */
const mockAxios = new MockAdapter(axios);

/*
 * The two wrapper-isolation tests below assert on spies for the grid and
 * static table, and on a simplified loading dialog. The remaining integration
 * tests render the real component tree (via the data story) against the vitest
 * `@mui/x-data-grid` stub, so the mocks delegate to the real implementations
 * unless a test opts in to the stubbed behaviour.
 */
const stoichiometryTableGridSpy = vi.fn();
const staticStoichiometryTableSpy = vi.fn();
const useWrapperStubs = { current: false };

vi.mock("@/tinyMCE/stoichiometry/table/StoichiometryTableGrid", async () => {
  const actual = await vi.importActual<typeof import("@/tinyMCE/stoichiometry/table/StoichiometryTableGrid")>(
    "@/tinyMCE/stoichiometry/table/StoichiometryTableGrid",
  );
  return {
    default: (props: React.ComponentProps<typeof actual.default>) => {
      stoichiometryTableGridSpy(props);
      if (useWrapperStubs.current) {
        // biome-ignore lint/a11y/useSemanticElements: test stub mirrors MUI DataGrid ARIA markup
        return <div role="grid" aria-label="Stoichiometry table grid" />;
      }
      return <actual.default {...props} />;
    },
  };
});

vi.mock("@/tinyMCE/stoichiometry/table/StaticStoichiometryTable", async () => {
  const actual = await vi.importActual<typeof import("@/tinyMCE/stoichiometry/table/StaticStoichiometryTable")>(
    "@/tinyMCE/stoichiometry/table/StaticStoichiometryTable",
  );
  return {
    default: (props: React.ComponentProps<typeof actual.default>) => {
      staticStoichiometryTableSpy(props);
      if (useWrapperStubs.current) {
        return <div>{"Static stoichiometry table"}</div>;
      }
      return <actual.default {...props} />;
    },
  };
});

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog", async () => {
  const actual = await vi.importActual<typeof import("@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog")>(
    "@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog",
  );
  return {
    default: () =>
      useWrapperStubs.current ? (
        <div role="dialog" aria-label="common:stoichiometry.dialog.loadingMoleculeInformation">
          {"common:stoichiometry.dialog.loadingMoleculeInformation"}
        </div>
      ) : (
        <actual.default />
      ),
  };
});

function makeMolecule(): EditableMolecule {
  return {
    id: 1,
    rsChemElement: null,
    inventoryLink: null,
    savedInventoryLink: null,
    deletedInventoryLink: null,
    role: "REACTANT",
    formula: null,
    name: "Cyclopentane",
    smiles: "C1CCCCC1",
    coefficient: 1,
    molecularWeight: 84.16,
    mass: 5,
    moles: null,
    actualAmount: 5,
    actualMoles: null,
    actualYield: null,
    limitingReagent: false,
    notes: null,
  };
}

/*
 * Mirrors the molecule data the Playwright spec served from
 * `/api/v1/stoichiometry`. The story queries the latest revision for
 * stoichiometryId 1.
 */
function createMockStoichiometryResponse() {
  return {
    id: 3,
    revision: 1,
    parentReaction: {
      id: 32769,
      parentId: 226,
      ecatChemFileId: null,
      dataImage: "mock-image-data",
      chemElements: "mock-chem-elements",
      smilesString: "C1=CC=CC=C1.C1C=CC=C1>>C1CCCCC1",
      chemId: null,
      reactionId: null,
      rgroupId: null,
      metadata: "{}",
      chemElementsFormat: "KET",
      creationDate: 1753964538000,
      imageFileProperty: {},
    },
    molecules: [
      {
        id: 4,
        rsChemElement: null,
        role: "REACTANT",
        formula: "C6 H6",
        name: "Benzene",
        smiles: "C1=CC=CC=C1",
        inventoryLink: null,
        coefficient: 1.0,
        molecularWeight: 1.0,
        mass: 10.0,
        actualAmount: 2.0,
        actualYield: 2.0,
        limitingReagent: true,
        notes: null,
      },
      {
        id: 5,
        rsChemElement: null,
        role: "REACTANT",
        formula: "C5 H6",
        name: "Cyclopentadiene",
        smiles: "C1C=CC=C1",
        inventoryLink: {
          id: 501,
          inventoryItemGlobalId: "SS123",
          stockDeducted: false,
        },
        coefficient: 2.0,
        molecularWeight: 1.0,
        mass: 10.0,
        actualAmount: 5.0,
        actualYield: 5.0,
        limitingReagent: false,
        notes: null,
      },
      {
        id: 6,
        rsChemElement: null,
        role: "PRODUCT",
        formula: "C6 H12",
        name: "Cyclopentane",
        smiles: "C1CCCCC1",
        inventoryLink: {
          id: 502,
          inventoryItemGlobalId: "SS124",
          stockDeducted: false,
        },
        coefficient: 3.0,
        molecularWeight: 1.0,
        mass: 10.0,
        actualAmount: 5.0,
        actualYield: 5.0,
        limitingReagent: false,
        notes: null,
      },
      {
        id: 7,
        rsChemElement: null,
        role: "AGENT",
        formula: "C2 H6 O",
        name: "Ethanol",
        smiles: "CCO",
        inventoryLink: {
          id: 503,
          inventoryItemGlobalId: "SS125",
          stockDeducted: false,
        },
        coefficient: 1.0,
        molecularWeight: 46.07,
        mass: 5.0,
        actualAmount: 5.0,
        actualYield: null,
        limitingReagent: false,
        notes: null,
      },
    ],
  };
}

const subSampleResponses: Record<number, unknown> = {
  123: { id: 123, globalId: "SS123", quantity: { numericValue: 4, unitId: 7 } },
  124: { id: 124, globalId: "SS124", quantity: { numericValue: 10, unitId: 7 } },
  125: { id: 125, globalId: "SS125", quantity: { numericValue: 25, unitId: 3 } },
};

function oauthToken() {
  const payload = {
    iss: "http://localhost:8080",
    iat: Date.now(),
    exp: Math.floor(Date.now() / 1000) + 300,
    refreshTokenHash: "fe15fa3d5e3d5a47e33e9e34229b1ea2314ad6e6f13fa42addca4f1439582a4d",
  };
  return Jwt.sign(payload, "dummySecretKey");
}

function routeNetwork() {
  server.use(
    http.get("/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: oauthToken() })),
    http.get("/api/v1/stoichiometry", () => HttpResponse.json(createMockStoichiometryResponse())),
    http.post("/api/v1/stoichiometry/molecule/info", () => HttpResponse.json(createMockStoichiometryResponse())),
    http.get("/api/inventory/v1/subSamples/:id", ({ params }) => {
      const body = subSampleResponses[Number(params.id)];
      return body
        ? HttpResponse.json(body)
        : HttpResponse.json({ message: "Not Found" }, { status: 404, statusText: "Not Found" });
    }),
  );
}

/**
 * Waits for the data story's React Query suspense + molecule-info bootstrap to
 * settle and the grid to appear.
 */
async function renderLoadedTable() {
  const user = userEvent.setup();
  const view = render(<StoichiometryTableWithDataStory />);
  const grid = await screen.findByRole("grid", undefined, { timeout: 10000 });
  await waitFor(() => {
    expect(screen.queryByText("common:stoichiometry.dialog.loadingMoleculeInformation")).not.toBeInTheDocument();
  });
  return { ...view, grid, user };
}

function getColumnHeaders(): string[] {
  return screen.getAllByRole("columnheader").map((header) => header.textContent?.trim() ?? "");
}

function getDataRow(name: string): HTMLElement {
  const row = screen.getAllByRole("row").find((candidate) => within(candidate).queryByText(name) !== null);
  if (!(row instanceof HTMLElement)) {
    throw new Error(`Data row not found: ${name}`);
  }
  return row;
}

describe("StoichiometryTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useWrapperStubs.current = false;
    mockAxios.reset();
    mockAxios.onGet("/api/v1/userDetails/uiNavigationData").reply(200, uiNavigationData());
    mockAxios.onGet("/session/ajax/livechatProperties").reply(200, { livechatEnabled: false });
    mockAxios.onAny().reply(200, {});
  });

  describe("wrapper rendering", () => {
    beforeEach(() => {
      useWrapperStubs.current = true;
    });

    it("renders a loading dialog while editable molecule info is being fetched", () => {
      const controller: StoichiometryTableController = {
        allMolecules: [makeMolecule()],
        linkedInventoryQuantityInfoByGlobalId: new Map(),
        isGettingMoleculeInfo: true,
        addReagent: vi.fn(() => Promise.resolve()),
        deleteReagent: vi.fn(),
        updateInventoryStock: vi.fn(() => Promise.resolve({ results: [] })),
        pickInventoryLink: vi.fn(),
        removeInventoryLink: vi.fn(),
        undoRemoveInventoryLink: vi.fn(),
        selectLimitingReagent: vi.fn(),
        processRowUpdate: vi.fn((newRow: EditableMolecule) => newRow),
      };

      render(
        <StoichiometryTableControllerProvider value={controller}>
          <StoichiometryTable editable stoichiometryId={3} stoichiometryRevision={1} activeChemId={123} />
        </StoichiometryTableControllerProvider>,
      );

      expect(
        screen.getByRole("dialog", { name: "common:stoichiometry.dialog.loadingMoleculeInformation" }),
      ).toBeVisible();
      expect(stoichiometryTableGridSpy).not.toHaveBeenCalled();
    });

    it("delegates read-only rendering to StaticStoichiometryTable", () => {
      render(<StoichiometryTable stoichiometryId={3} stoichiometryRevision={1} />);

      expect(screen.getByText("Static stoichiometry table")).toBeVisible();
      expect(staticStoichiometryTableSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          stoichiometryId: 3,
          stoichiometryRevision: 1,
          editable: false,
        }),
      );
    });
  });

  describe("editable table rendered with data", () => {
    beforeEach(() => {
      // Render the real component tree against the data-grid stub rather than
      // the simplified wrapper stubs. The outer beforeEach already resets this,
      // but set it explicitly so the intent survives any future reordering or
      // file-level test concurrency.
      useWrapperStubs.current = false;
      routeNetwork();
    });

    it("has no accessibility violations", async () => {
      const { baseElement } = await renderLoadedTable();

      await expectAccessible(baseElement);
    });

    it("renders the table and displays molecule data", async () => {
      await renderLoadedTable();

      expect(screen.getByRole("grid")).toBeVisible();
      expect(screen.getByText("Benzene")).toBeVisible();
      // header row + 4 data rows
      expect(screen.getAllByRole("row").length).toBeGreaterThan(1);
    });

    it("displays the expected column headers", async () => {
      await renderLoadedTable();

      const headers = getColumnHeaders();
      expect(headers).toContain("common:stoichiometry.table.columns.name");
      expect(headers).toContain("common:stoichiometry.table.columns.inventoryLink");
      expect(headers).toContain("common:stoichiometry.table.columns.type");
      expect(headers).toContain("common:stoichiometry.table.columns.limitingReagent");
      expect(headers).toContain("common:stoichiometry.table.columns.equivalent");
      expect(headers).toContain("common:stoichiometry.table.columns.molecularWeight");
      expect(headers).toContain("common:stoichiometry.table.columns.mass");
      expect(headers).toContain("common:stoichiometry.table.columns.moles");
      expect(headers).toContain("common:stoichiometry.table.columns.notes");
    });

    it("shows inventory link controls for both linked and unlinked molecules", async () => {
      await renderLoadedTable();

      expect(screen.getAllByLabelText("common:stoichiometry.inventoryLink.removeForMolecule").length).toBeGreaterThan(
        0,
      );
      expect(screen.getAllByLabelText("common:stoichiometry.inventoryLink.addForMolecule").length).toBeGreaterThan(0);
    });

    it("opens and closes the inventory picker from an unlinked molecule", async () => {
      const { user } = await renderLoadedTable();

      await user.click(screen.getByLabelText("common:stoichiometry.inventoryLink.addForMolecule"));
      expect(
        await screen.findByRole("dialog", {
          name: "common:stoichiometry.inventoryLink.pickerTitle",
        }),
      ).toBeVisible();

      await user.click(screen.getByRole("button", { name: "common:actions.cancel" }));
      await waitFor(() => {
        expect(
          screen.queryByRole("dialog", {
            name: "common:stoichiometry.inventoryLink.pickerTitle",
          }),
        ).not.toBeVisible();
      });
    });

    it("selects the first reactant as the default limiting reagent", async () => {
      await renderLoadedTable();

      const benzeneRow = getDataRow("Benzene");
      expect(
        within(benzeneRow).getByRole("radio", {
          name: "common:stoichiometry.table.label.selectLimitingReagent",
        }),
      ).toBeChecked();
    });

    it("provides a menu for exporting the stoichiometry table to CSV", async () => {
      const { user } = await renderLoadedTable();

      await user.click(screen.getByRole("button", { name: "common:actions.export" }));
      expect(await screen.findByRole("tooltip")).toBeVisible();
      expect(screen.getByRole("menuitem", { name: "common:stoichiometry.tableToolbar.exportToCsv" })).toBeVisible();
    });

    it("opens the Add Chemical menu with PubChem, Gallery and manual options", async () => {
      const { user } = await renderLoadedTable();

      await user.click(screen.getByRole("button", { name: "common:stoichiometry.addReagent.addChemical" }));

      expect(
        await screen.findByRole("menuitem", {
          name: "common:stoichiometry.addReagent.sources.pubChem.title common:stoichiometry.addReagent.sources.pubChem.subheader",
        }),
      ).toBeVisible();
      expect(
        screen.getByRole("menuitem", {
          name: "common:stoichiometry.addReagent.sources.gallery.title common:stoichiometry.addReagent.sources.gallery.subheader",
        }),
      ).toBeVisible();
      expect(
        screen.getByRole("menuitem", {
          name: "common:stoichiometry.addReagent.sources.manual.title common:stoichiometry.addReagent.sources.manual.subheader",
        }),
      ).toBeVisible();
    });

    it("opens the PubChem dialog from the Add Chemical menu", async () => {
      const { user } = await renderLoadedTable();

      await user.click(screen.getByRole("button", { name: "common:stoichiometry.addReagent.addChemical" }));
      await user.click(
        await screen.findByRole("menuitem", {
          name: "common:stoichiometry.addReagent.sources.pubChem.title common:stoichiometry.addReagent.sources.pubChem.subheader",
        }),
      );

      expect(await screen.findByText("workspace:tinymce.pubchem.dialog.title")).toBeVisible();
    });

    it("opens the manual SMILES dialog from the Add Chemical menu", async () => {
      const { user } = await renderLoadedTable();

      await user.click(screen.getByRole("button", { name: "common:stoichiometry.addReagent.addChemical" }));
      await user.click(
        await screen.findByRole("menuitem", {
          name: "common:stoichiometry.addReagent.sources.manual.title common:stoichiometry.addReagent.sources.manual.subheader",
        }),
      );

      expect(await screen.findByRole("dialog", { name: "common:stoichiometry.addReagent.title" })).toBeVisible();
    });

    it("opens the Gallery dialog from the Add Chemical menu", async () => {
      const { user } = await renderLoadedTable();

      await user.click(screen.getByRole("button", { name: "common:stoichiometry.addReagent.addChemical" }));
      await user.click(
        await screen.findByRole("menuitem", {
          name: "common:stoichiometry.addReagent.sources.gallery.title common:stoichiometry.addReagent.sources.gallery.subheader",
        }),
      );

      // The Gallery picker is a React.lazy import with a large dependency
      // graph; allow extra time for the chunk to load and render before the
      // dialog appears (the sibling PubChem/SMILES dialogs are not lazy).
      expect(
        await screen.findByRole("dialog", { name: "common:appBar.sections.gallery.title" }, { timeout: 10000 }),
      ).toBeVisible();
    });

    it("keeps Update Inventory Stock disabled until inventory quantities load", async () => {
      let releaseSubSampleResponses: () => void = () => {};
      const subSampleGate = new Promise<void>((resolve) => {
        releaseSubSampleResponses = resolve;
      });
      server.use(
        http.get("/api/inventory/v1/subSamples/:id", async ({ params }) => {
          await subSampleGate;
          const body = subSampleResponses[Number(params.id)];
          return body
            ? HttpResponse.json(body)
            : HttpResponse.json({ message: "Not Found" }, { status: 404, statusText: "Not Found" });
        }),
      );

      await renderLoadedTable();

      const button = screen.getByRole("button", { name: "common:stoichiometry.inventoryUpdate.updateInventoryStock" });
      expect(button).toBeDisabled();

      releaseSubSampleResponses();

      await waitFor(() => {
        expect(button).toBeEnabled();
      });
    });

    it("opens the inventory stock update dialog listing the current molecules", async () => {
      const { user } = await renderLoadedTable();

      const updateStockButton = screen.getByRole("button", {
        name: "common:stoichiometry.inventoryUpdate.updateInventoryStock",
      });
      await waitFor(() => {
        expect(updateStockButton).toBeEnabled();
      });
      await user.click(updateStockButton);

      const dialog = await screen.findByRole("dialog", {
        name: "common:stoichiometry.inventoryUpdate.dialogTitle",
      });
      expect(dialog).toBeVisible();
      expect(within(dialog).getByText("Benzene")).toBeVisible();
      expect(within(dialog).getByText("Cyclopentadiene")).toBeVisible();
      expect(within(dialog).getByText("Cyclopentane")).toBeVisible();
      expect(within(dialog).getByText("Ethanol")).toBeVisible();
    });
  });
});
