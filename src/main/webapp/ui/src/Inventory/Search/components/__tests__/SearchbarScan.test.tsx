import "@/stores/stores/RootStore";
import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay } from "es-toolkit";
import { describe, expect, test, vi } from "vitest";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import Search from "../../../../stores/models/Search";
import materialTheme from "../../../../theme";
import type { Barcode, BarcodeFormat } from "../../../../util/barcode";
import { visitUrl } from "../../../../util/Util";
import Searchbar from "../Searchbar";

import "@/__tests__/__mocks__/resizeObserver";

/*
 * jsdom's window.location is not configurable, so full-page navigation is
 * asserted by mocking the visitUrl wrapper instead.
 */
vi.mock("../../../../util/Util", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../../util/Util")>();
  return { ...actual, visitUrl: vi.fn() };
});

/*
 * A local BarcodeDetector mock rather than the shared
 * @/__tests__/__mocks__/barcode-detection-api one, because these tests need
 * to vary the scanned value between tests and the shared mock's value is
 * fixed.
 */
let mockScannedValue = "foo";
class MockBarcodeDetector {
  detect(): Promise<Array<Barcode>> {
    return Promise.resolve([
      {
        rawValue: mockScannedValue,
        format: "qr_code",
      },
    ]);
  }

  getSupportedFormats(): Promise<Array<BarcodeFormat>> {
    return Promise.reject();
  }
}
Object.defineProperty(window, "BarcodeDetector", {
  writable: false,
  value: MockBarcodeDetector,
});

function renderSearchbar({
  search,
  handleSearch = () => {},
}: {
  search: Search;
  handleSearch?: (query: string) => void;
}) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <SearchContext.Provider
        value={{
          search,
          differentSearchForSettingActiveResult: search,
        }}
      >
        <Searchbar handleSearch={handleSearch} />
      </SearchContext.Provider>
    </ThemeProvider>,
  );
}

async function scanIntoOpenScanner(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "inventory:search.controls.searchbar.scanBarcode" }));
  /*
   * Wait a second because the barcode scanner checks for a barcode once per
   * second. The extra 100ms is just to ensure that this code doesn't execute
   * before the detection completes.
   */
  await act(async () => {
    await delay(1100);
  });
  await user.click(screen.getByRole("button", { name: "inventory:search.controls.searchbar.scanSearch" }));
}

describe("Searchbar barcode scanning", () => {
  test("When the SCAN module is allowed, the scan button and scan placeholder are shown.", () => {
    const search = new Search({
      factory: mockFactory(),
    });
    renderSearchbar({ search });
    expect(screen.getByRole("button", { name: "inventory:search.controls.searchbar.scanBarcode" })).toBeVisible();
    expect(screen.getByRole("searchbox")).toHaveAttribute(
      "placeholder",
      "inventory:search.controls.searchbar.searchOrScan",
    );
  });

  test("When the SCAN module is not allowed, there is no scan button and the plain placeholder is shown.", () => {
    const search = new Search({
      factory: mockFactory(),
      uiConfig: {
        allowedSearchModules: new Set(),
      },
    });
    renderSearchbar({ search });
    expect(
      screen.queryByRole("button", { name: "inventory:search.controls.searchbar.scanBarcode" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("searchbox")).toHaveAttribute("placeholder", "inventory:search.controls.searchbar.search");
  });

  test("Scanning a barcode fills the query and performs the search.", async () => {
    const user = userEvent.setup();
    vi.spyOn(HTMLVideoElement.prototype, "play").mockImplementation(() => Promise.resolve());
    mockScannedValue = "BC-1234";
    const handleSearch = vi.fn<(query: string) => void>();
    const search = new Search({
      factory: mockFactory(),
    });
    renderSearchbar({ search, handleSearch });

    await scanIntoOpenScanner(user);

    expect(handleSearch).toHaveBeenCalledWith("BC-1234");
    expect(search.fetcher.query).toBe("BC-1234");
  });

  test("Scanning an Inventory permalink navigates to the item instead of searching.", async () => {
    const user = userEvent.setup();
    vi.spyOn(HTMLVideoElement.prototype, "play").mockImplementation(() => Promise.resolve());
    mockScannedValue = "https://example.com/inventory/sample/123";
    const handleSearch = vi.fn<(query: string) => void>();
    const search = new Search({
      factory: mockFactory(),
    });
    renderSearchbar({ search, handleSearch });

    await scanIntoOpenScanner(user);

    expect(vi.mocked(visitUrl)).toHaveBeenCalledWith("https://example.com/inventory/sample/123");
    expect(handleSearch).not.toHaveBeenCalled();
  });
});
