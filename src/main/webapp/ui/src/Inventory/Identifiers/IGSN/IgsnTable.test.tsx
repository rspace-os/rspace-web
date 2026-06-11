import { describe, test, expect, beforeEach, afterEach } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import React from "react";
import { render, screen, waitFor, within, expectAccessible} from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import identifiersJson from "../../__tests__/identifiers.json";
import {
  SimpleIgsnTable,
  SingularSelectionIgsnTable,
  IgsnTableWithControlDefaults,
} from "./IgsnTable.story";

const mockAxios = new MockAdapter(axios);

/**
 * Mirrors the Playwright spec's `router.route("/api/inventory/v1/identifiers")`
 * stub. The production hook serialises `searchTerm` as the `identifier` query
 * parameter, so we filter on that.
 */
function setupIdentifiersEndpoint(rows: typeof identifiersJson = identifiersJson) {
  mockAxios.onGet("/api/inventory/v1/identifiers").reply((config) => {
    const params = (config.params ?? new URLSearchParams()) as URLSearchParams;
    const state = params.get("state");
    const isAssociated = params.get("isAssociated");
    const searchTerm = params.get("identifier");

    let filteredIdentifiers = rows;
    if (state) {
      filteredIdentifiers = filteredIdentifiers.filter(
        (identifier) => identifier.state === state
      );
    }
    if (searchTerm) {
      filteredIdentifiers = filteredIdentifiers.filter((identifier) =>
        identifier.doi.includes(searchTerm)
      );
    }
    if (isAssociated === "true") {
      filteredIdentifiers = filteredIdentifiers.filter(
        (identifier) => identifier.associatedGlobalId !== null
      );
    } else if (isAssociated === "false") {
      filteredIdentifiers = filteredIdentifiers.filter(
        (identifier) => identifier.associatedGlobalId === null
      );
    }
    return [200, filteredIdentifiers];
  });
}

/**
 * The query parameters of every GET request made to the identifiers endpoint,
 * in order. The Playwright spec captured these via `page.on("request")`.
 */
function identifiersRequestParams(): Array<URLSearchParams> {
  return mockAxios.history.get
    .filter(({ url }) => url === "/api/inventory/v1/identifiers")
    .map(({ params }) => (params ?? new URLSearchParams()) as URLSearchParams);
}

/**
 * Waits for the table to finish its initial load: either data rows appear
 * (more than the single header row) or the empty-state message is shown.
 */
async function waitForTableLoaded() {
  await waitFor(() => {
    const rows = screen.getAllByRole("row");
    const hasEmptyState = screen.queryByText("No IGSN IDs") !== null;
    expect(rows.length > 1 || hasEmptyState).toBe(true);
  });
}

describe("IGSN Table", () => {
  beforeEach(() => {
    mockAxios.resetHistory();
    setupIdentifiersEndpoint();
  });

  afterEach(() => {
    mockAxios.reset();
  });

  test("When the researcher is viewing the IGSN table, a table should be shown.", () => {
    render(<SimpleIgsnTable />);
    expect(screen.getByRole("grid")).toBeVisible();
  });

  test("The default columns should be Select, DOI, State, and Linked Item", () => {
    render(<SimpleIgsnTable />);
    const headers = screen
      .getAllByRole("columnheader")
      .map((header) => header.textContent);
    // the empty string at the beginning is the checkbox column
    expect(headers).toEqual(["Select", "DOI", "State", "Linked Item"]);
  });

  test("The mocked data displays four rows", async () => {
    render(<SimpleIgsnTable />);
    await waitForTableLoaded();
    // + 1 for the header row
    expect(screen.getAllByRole("row")).toHaveLength(5);
  });

  test("The toolbar should contain a search box", () => {
    render(<SimpleIgsnTable />);
    expect(screen.getByRole("searchbox")).toBeVisible();
  });

  test("The toolbar's search box should have the correct placeholder text", () => {
    render(<SimpleIgsnTable />);
    /*
     * International Generic Sample Number (IGSN), confusingly, refers to the
     * organization and the IDs themselves are referred to as IGSN IDs.
     */
    expect(screen.getByRole("searchbox")).toHaveAttribute(
      "placeholder",
      "Search IGSN IDs..."
    );
  });

  test("Searching makes API call with searchTerm parameter", async () => {
    const user = userEvent.setup();
    render(<SimpleIgsnTable />);
    await waitForTableLoaded();

    await user.type(screen.getByRole("searchbox"), "test");

    await waitFor(() => {
      expect(
        identifiersRequestParams().some(
          (params) => params.get("identifier") === "test"
        )
      ).toBe(true);
    });
  });

  test("There should be a menu for changing column visibility", async () => {
    const user = userEvent.setup();
    render(<SimpleIgsnTable />);

    await user.click(screen.getByRole("button", { name: "Select columns" }));
    expect(screen.getByRole("menu")).toBeVisible();
  });

  test("There should be a menu for exporting the IGSN table to CSV", async () => {
    const user = userEvent.setup();
    render(<SimpleIgsnTable />);

    await user.click(screen.getByRole("button", { name: "Export" }));
    expect(screen.getByRole("menu")).toBeVisible();
  });

  test("Filtering by state makes API call with state parameter", async () => {
    const user = userEvent.setup();
    render(<SimpleIgsnTable />);
    await waitForTableLoaded();

    await user.click(screen.getByRole("button", { name: /State:/ }));
    await user.click(screen.getByRole("menuitem", { name: /Draft/ }));

    await waitFor(() => {
      expect(
        identifiersRequestParams().some(
          (params) => params.get("state") === "draft"
        )
      ).toBe(true);
    });
  });

  test("Filtering by linked item makes API call with isAssociated parameter", async () => {
    const user = userEvent.setup();
    render(<SimpleIgsnTable />);
    await waitForTableLoaded();

    await user.click(screen.getByRole("button", { name: /Linked Item:/ }));
    await user.click(screen.getByRole("menuitem", { name: /No Linked Item/ }));

    await waitFor(() => {
      expect(
        identifiersRequestParams().some(
          (params) => params.get("isAssociated") === "false"
        )
      ).toBe(true);
    });
  });

  test("When a researcher selects an identifier, the selection state is updated", async () => {
    const user = userEvent.setup();
    render(<SimpleIgsnTable />);
    await waitForTableLoaded();

    const row = screen
      .getAllByRole("row")
      .find((r) => r.textContent?.includes("10.82316/khma-em96"));
    if (!row) throw new Error("Could not find row for DOI 10.82316/khma-em96");
    const checkbox = within(row).getByRole("checkbox", { name: /Select row/ });
    await user.click(checkbox);

    /*
     * IgsnTable.story renders the selection, so we check what's been rendered
     * to confirm setSelectedIdentifiers has been called.
     */
    expect(
      within(screen.getByLabelText("selected IGSNs")).getByText(
        "10.82316/khma-em96"
      )
    ).toBeVisible();
    expect(checkbox).toBeChecked();
  });

  test("When a researcher selects an identifier in singular selection mode, the selection state is updated", async () => {
    const user = userEvent.setup();
    render(<SingularSelectionIgsnTable />);
    await waitForTableLoaded();

    const row = screen
      .getAllByRole("row")
      .find((r) => r.textContent?.includes("10.82316/khma-em96"));
    if (!row) throw new Error("Could not find row for DOI 10.82316/khma-em96");
    const radio = within(row).getByRole("radio");
    await user.click(radio);

    expect(
      within(screen.getByLabelText("selected IGSNs")).getByText(
        "10.82316/khma-em96"
      )
    ).toBeVisible();
    expect(radio).toBeChecked();
  });

  test("Control defaults are applied to the table when provided", async () => {
    render(<IgsnTableWithControlDefaults />);
    await waitForTableLoaded();

    await waitFor(() => {
      const params = identifiersRequestParams();
      expect(
        params.some((p) => p.get("state") === "draft")
      ).toBe(true);
      expect(
        params.some((p) => p.get("isAssociated") === "false")
      ).toBe(true);
      expect(
        params.some((p) => p.get("identifier") === "test")
      ).toBe(true);
    });
  });

  test("The Linked Item column should contain links to the Inventory record", async () => {
    render(<SimpleIgsnTable />);
    await waitForTableLoaded();

    const table = screen.getByRole("grid");
    const allRows = within(table).getAllByRole("row");
    const headerRow = allRows[0];
    const headers = within(headerRow).getAllByRole("columnheader");
    const linkedItemColumnIndex = headers.findIndex(
      (header) => header.textContent?.trim() === "Linked Item"
    );
    expect(linkedItemColumnIndex).not.toBe(-1);

    const dataRows = allRows.slice(1);
    expect(dataRows.length).toBeGreaterThan(0);
    for (const dataRow of dataRows) {
      const cell = within(dataRow).getAllByRole("gridcell")[
        linkedItemColumnIndex
      ];
      expect(cell.querySelectorAll("a").length).toBeGreaterThan(0);
    }
  });

  test("When there are no results, a 'No IGSN IDs' message is displayed", async () => {
    setupIdentifiersEndpoint([]);
    render(<SimpleIgsnTable />);
    await waitForTableLoaded();

    // Verify the overlay message is displayed
    expect(
      within(screen.getByRole("grid")).getByText("No IGSN IDs")
    ).toBeVisible();
    // Verify the grid has a header row but no data rows
    const headerRow = screen
      .getAllByRole("row")
      .filter(
        (row) => within(row).queryAllByRole("columnheader").length > 0
      );
    expect(headerRow).toHaveLength(1);
    expect(headerRow[0]).toBeVisible();
  });

  test("Scanning a QR code updates the search term", async () => {
    const user = userEvent.setup();
    render(<SimpleIgsnTable />);
    await waitForTableLoaded();

    await user.click(screen.getByRole("button", { name: "Scan" }));
    /*
     * Since we can't easily mock the camera API in tests, we simulate manual
     * entry which is an alternative in the UI.
     */
    await user.type(
      await screen.findByRole("textbox", {
        name: "Alternatively, enter the data encoded in the barcode",
      }),
      "test"
    );
    await user.click(screen.getByRole("button", { name: /Search for IGSN/ }));

    await waitFor(() => {
      expect(
        identifiersRequestParams().some(
          (params) => params.get("identifier") === "test"
        )
      ).toBe(true);
    });
  });

  test("The toolbar controls should be in the order: search, scan, then filters", () => {
    /*
     * This is so that the controls are in a consistent order across the whole
     * product.
     */
    render(<SimpleIgsnTable />);

    const search = screen.getByRole("searchbox");
    const scan = screen.getByRole("button", { name: "Scan" });
    const state = screen.getByRole("button", { name: /State:/ });
    const linkedItem = screen.getByRole("button", { name: /Linked Item:/ });

    expect(search).toBeVisible();
    expect(scan).toBeVisible();
    expect(state).toBeVisible();
    expect(linkedItem).toBeVisible();

    expect(
      Boolean(
        search.compareDocumentPosition(scan) & Node.DOCUMENT_POSITION_FOLLOWING
      )
    ).toBe(true);
    expect(
      Boolean(
        scan.compareDocumentPosition(state) & Node.DOCUMENT_POSITION_FOLLOWING
      )
    ).toBe(true);
    expect(
      Boolean(
        state.compareDocumentPosition(linkedItem) &
          Node.DOCUMENT_POSITION_FOLLOWING
      )
    ).toBe(true);
  });

  test("Should have no axe violations.", async () => {
    const { baseElement } = render(<SimpleIgsnTable />);
    await waitForTableLoaded();

    await expectAccessible(baseElement);
  });
});
