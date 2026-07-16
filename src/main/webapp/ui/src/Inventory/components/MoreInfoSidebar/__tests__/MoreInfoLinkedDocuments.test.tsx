import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen, within } from "@testing-library/react";
import type { AxiosResponse } from "axios";
import { afterEach, describe, expect, test, vi } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { findTableCell } from "@/__tests__/tableQueries";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import InvApiService from "../../../../common/InvApiService";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import { newDocument } from "../../../../stores/models/Document";
import materialTheme from "../../../../theme";
import LinkedDocuments from "../LinkedDocuments";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    get: () => ({}),
  },
}));
describe("LinkedDocuments", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });
  test("fetches a template's back-references via the generic referencingItems endpoint", async () => {
    // there is no typed /sampleTemplates/{id}/referencingItems endpoint, so
    // IT targets use the generic /referencingItems/{globalId} route
    const spy = vi.spyOn(InvApiService, "get").mockImplementation((url) => {
      if (String(url).startsWith("referencingItems/")) {
        return Promise.resolve({
          data: {
            referencingItems: [
              {
                sourceGlobalId: "SA1",
                sourceName: "A sample",
                sourceType: "SAMPLE",
                relationType: "References",
                versionPin: null,
              },
            ],
          },
        } as AxiosResponse);
      }
      return Promise.resolve({ data: [] } as AxiosResponse);
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments factory={mockFactory()} globalId="IT5" />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));

    expect(await screen.findByText("A sample")).toBeVisible();
    expect(spy).toHaveBeenCalledWith("listOfMaterials/forInventoryItem/IT5");
    expect(spy).toHaveBeenCalledWith("referencingItems/IT5");
  });

  test("Assert that correct API endpoint is called with Global ID", async () => {
    const spy = vi.spyOn(InvApiService, "get").mockImplementation(() => Promise.reject(new Error("An error")));
    render(<LinkedDocuments factory={mockFactory()} globalId="IC1" />);
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    expect(await screen.findByText("An error")).toBeVisible();
    expect(spy).toHaveBeenCalledWith("listOfMaterials/forInventoryItem/IC1");
  });
  test("When there is an error loading the data, an alert should be shown.", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() => Promise.reject(new Error("An error")));
    render(<LinkedDocuments factory={mockFactory()} globalId="IC1" />);
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("An error");
  });
  test("Two different documents should render as two table rows", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() => {
      return Promise.resolve({
        data: [
          { elnDocument: { globalId: "SD1", id: 1, name: "Foo", owner: null } },
          { elnDocument: { globalId: "SD2", id: 2, name: "Bar", owner: null } },
        ],
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse);
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments
          factory={mockFactory({
            newDocument: (x) => newDocument(x),
          })}
          globalId="IC1"
        />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    expect(within(await screen.findByRole("table")).getAllByRole("row")).toHaveLength(3);
    expect(
      await findTableCell(screen.getByRole("table"), {
        columnHeading: "inventory:moreInfo.linkedDocuments.columns.name",
        rowIndex: 0,
      }),
    ).toHaveTextContent("Foo");
    expect(
      await findTableCell(screen.getByRole("table"), {
        columnHeading: "inventory:moreInfo.linkedDocuments.columns.name",
        rowIndex: 1,
      }),
    ).toHaveTextContent("Bar");
  });
  test("Two of the same document should render as one table row", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() => {
      return Promise.resolve({
        data: [
          { elnDocument: { globalId: "SD1", id: 1, name: "Foo", owner: null } },
          { elnDocument: { globalId: "SD1", id: 1, name: "Foo", owner: null } },
        ],
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse);
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments
          factory={mockFactory({
            newDocument: (x) => newDocument(x),
          })}
          globalId="IC1"
        />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    const rows = within(await screen.findByRole("table")).getAllByRole("row");

    expect(rows).toHaveLength(2);
    expect(
      await findTableCell(screen.getByRole("table"), {
        columnHeading: "inventory:moreInfo.linkedDocuments.columns.name",
        rowIndex: 0,
      }),
    ).toHaveTextContent("Foo");
  });
  test.each([
    ["SS9", "subSamples/9/referencingItems"],
    ["IC5", "containers/5/referencingItems"],
    ["IN3", "instruments/3/referencingItems"],
  ])("Maps Global ID %s to %s for the referencingItems endpoint", async (globalId, expectedUrl) => {
    const spy = vi.spyOn(InvApiService, "get").mockImplementation((url) => {
      if (typeof url === "string" && url.endsWith("/referencingItems")) {
        return Promise.resolve({
          data: { referencingItems: [] },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse);
      }
      return Promise.resolve({
        data: [],
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse);
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments factory={mockFactory()} globalId={globalId} />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    await screen.findByRole("button", { name: "common:actions.close" });
    expect(spy).toHaveBeenCalledWith(expectedUrl);
  });

  test("Does not call referencingItems for an unknown Global ID prefix", async () => {
    const spy = vi.spyOn(InvApiService, "get").mockImplementation(() => {
      return Promise.resolve({
        data: [],
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse);
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments factory={mockFactory()} globalId="ZZ1" />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    await screen.findByRole("button", { name: "common:actions.close" });
    const calls = spy.mock.calls.map(([url]) => url);
    expect(calls.some((url) => typeof url === "string" && url.endsWith("/referencingItems"))).toBe(false);
  });

  test("When no documents link to the item, the empty-state mentions both List of Materials and inventory links", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() => {
      return Promise.resolve({
        data: [],
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse);
    });
    await renderWithRealI18n(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments factory={mockFactory()} globalId="IC1" />
      </ThemeProvider>,
      { resources: { common: commonEn, inventory: inventoryEn }, defaultNS: "inventory" },
    );
    fireEvent.click(screen.getByRole("button", { name: "Show Linked Documents" }));
    const listOfMaterialsLink = await screen.findByRole("link", { name: "List of Materials" });
    expect(listOfMaterialsLink).toHaveAttribute("href", expect.stringContaining("list-of-materials"));
    expect(
      await screen.findByText(
        "Other Inventory items that link to this item through a Link custom field will also be listed here.",
      ),
    ).toBeVisible();
  });
  test("Opening the dialog twice should trigger two network calls", async () => {
    const spy = vi.spyOn(InvApiService, "get").mockImplementation(() => {
      return Promise.reject(new Error("An error"));
    });
    render(<LinkedDocuments factory={mockFactory()} globalId="IC1" />);
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    expect(await screen.findByRole("button", { name: "common:actions.close" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "common:actions.close" }));
    await screen.findByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" });
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    expect(await screen.findByText("An error")).toBeVisible();
    // 2 opens, each open triggers 2 calls (documents + referencing items)
    expect(spy).toHaveBeenCalledTimes(4);
  });

  test("Calls the referencingItems endpoint for the matching item kind", async () => {
    const spy = vi.spyOn(InvApiService, "get").mockImplementation((url) => {
      if (typeof url === "string" && url.endsWith("/referencingItems")) {
        return Promise.resolve({
          data: { referencingItems: [] },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse);
      }
      return Promise.resolve({
        data: [],
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse);
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments factory={mockFactory()} globalId="SA42" />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    // wait for the dialog to settle
    await screen.findByRole("button", { name: "common:actions.close" });
    expect(spy).toHaveBeenCalledWith("samples/42/referencingItems");
  });

  test("Renders a row per referencing Inventory item", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation((url) => {
      if (typeof url === "string" && url.endsWith("/referencingItems")) {
        return Promise.resolve({
          data: {
            referencingItems: [
              {
                sourceGlobalId: "SA10",
                sourceName: "Calibrator A",
                sourceType: "SAMPLE",
                relationType: "IsCalibratedBy",
                versionPin: null,
                modifiedAtMillis: 0,
              },
              {
                sourceGlobalId: "IC5",
                sourceName: "Box 5",
                sourceType: "CONTAINER",
                relationType: "References",
                versionPin: 3,
                modifiedAtMillis: 0,
              },
            ],
          },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse);
      }
      return Promise.resolve({
        data: [],
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse);
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments factory={mockFactory()} globalId="IC1" />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    expect(await screen.findByText("Calibrator A")).toBeVisible();
    expect(screen.getByText("Box 5")).toBeVisible();
    expect(screen.getByText("IsCalibratedBy")).toBeVisible();
    expect(screen.getByText("References")).toBeVisible();
  });

  test("shows which version of THIS item each referencing item links to, not as a suffix on the source Global ID", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation((url) => {
      if (typeof url === "string" && url.endsWith("/referencingItems")) {
        return Promise.resolve({
          data: {
            referencingItems: [
              {
                sourceGlobalId: "SA10",
                sourceName: "Unpinned source",
                sourceType: "SAMPLE",
                relationType: "References",
                versionPin: null,
              },
              {
                sourceGlobalId: "IC5",
                sourceName: "Pinned source",
                sourceType: "CONTAINER",
                relationType: "References",
                versionPin: 3,
              },
            ],
          },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse);
      }
      return Promise.resolve({
        data: [],
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse);
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments factory={mockFactory()} globalId="IC1" />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "inventory:moreInfo.linkedDocuments.show" }));
    // The source's Global ID is shown bare: the version pin is NOT a version of the source.
    expect(await screen.findByText("IC5")).toBeVisible();
    expect(screen.queryByText("IC5v3")).not.toBeInTheDocument();
    // Each row shows, separately, which version of THIS item the source links to.
    expect(screen.getByText("v3")).toBeVisible();
    expect(screen.getByText("inventory:moreInfo.linkedDocuments.latest")).toBeVisible();
  });
});
