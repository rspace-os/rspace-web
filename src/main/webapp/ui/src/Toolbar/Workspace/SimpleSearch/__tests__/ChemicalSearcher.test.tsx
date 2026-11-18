import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import materialTheme from "../../../../theme";
import ChemicalSearcher from "../ChemicalSearcher";

// the Ketcher editor is exercised end to end; here it only hands over a structure
vi.mock("../../../../components/Ketcher/KetcherDialog", () => ({
  default: ({ handleInsert }: { handleInsert: (ketcher: { getSmiles: () => Promise<string> }) => void }) => (
    <button type="button" onClick={() => handleInsert({ getSmiles: () => Promise.resolve("c1ccccc1") })}>
      {"Search drawn structure"}
    </button>
  ),
}));

const galleryHit = {
  globalId: "GL5",
  recordName: "aminoglutethimide.mol",
  chemId: 7,
  breadcrumb: "/Gallery/Chemistry/aminoglutethimide.mol",
  owner: "Ann Owner",
  lastModified: "Wed Nov 18 10:00:00 GMT 2026",
  source: "GALLERY",
};

const inventoryHit = {
  globalId: "SA12",
  recordName: "aminoglutethimide batch",
  chemId: null,
  breadcrumb: "",
  owner: "Ann Owner",
  lastModified: "Wed Nov 18 11:00:00 GMT 2026",
  source: "INVENTORY",
  inventoryType: "SAMPLE",
};

beforeEach(() => {
  window.ketcher = { setMolecule: vi.fn(() => Promise.resolve()) } as unknown as typeof window.ketcher;
  server.use(
    http.post("*/chemical/search", () =>
      HttpResponse.json({
        chemSearchResultsPage: {
          hits: [galleryHit, inventoryHit],
          totalHitCount: 2,
          totalPageCount: 1,
          startHit: 0,
          endHit: 1,
        },
      }),
    ),
  );
});

describe("Workspace chemical search results", () => {
  test("list an Inventory item after the Gallery file, with its type icon and a link to it", async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ChemicalSearcher isOpen onClose={() => {}} />
      </ThemeProvider>,
    );

    await user.click(await screen.findByRole("button", { name: "Search drawn structure" }));

    const sampleLink = await screen.findByRole("link", { name: "aminoglutethimide batch" });
    expect(sampleLink).toHaveAttribute("href", "/globalId/SA12");
    const rows = screen.getAllByRole("row").filter((row) => within(row).queryAllByRole("gridcell").length > 0);
    expect(rows).toHaveLength(2);
    expect(within(rows[1]).getByRole("img", { name: "inventory:recordTypes.sample.singular" })).toBeVisible();
    expect(within(rows[0]).getByRole("link", { name: "Gallery/Chemistry/aminoglutethimide.mol" })).toBeVisible();
  });

  test("link a Gallery file to its Gallery item page, which opens with the file selected", async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ChemicalSearcher isOpen onClose={() => {}} />
      </ThemeProvider>,
    );

    await user.click(await screen.findByRole("button", { name: "Search drawn structure" }));

    expect(await screen.findByRole("link", { name: "Gallery/Chemistry/aminoglutethimide.mol" })).toHaveAttribute(
      "href",
      "/gallery/item/5",
    );
  });
});
