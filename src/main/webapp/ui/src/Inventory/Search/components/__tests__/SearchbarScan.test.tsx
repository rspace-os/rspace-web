import "@/stores/stores/RootStore";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import Search from "../../../../stores/models/Search";
import materialTheme from "../../../../theme";
import { visitUrl } from "../../../../util/Util";
import Searchbar from "../Searchbar";

import "@/__tests__/__mocks__/resizeObserver";
import { setMockScannedBarcode } from "@/__tests__/__mocks__/barcode-detection-api";

/*
 * jsdom's window.location is not configurable, so full-page navigation is
 * asserted by mocking the visitUrl wrapper instead.
 */
vi.mock("../../../../util/Util", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../../util/Util")>();
  return { ...actual, visitUrl: vi.fn() };
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
   * The scanner polls for a barcode once per second, then enables its confirm
   * button. Wait for that button to enable rather than sleeping a fixed time,
   * so the test isn't racing the detection interval (the poll interval is the
   * default waitFor timeout, so allow more headroom).
   */
  const confirmButton = screen.getByRole("button", { name: "inventory:search.controls.searchbar.scanConfirm" });
  await waitFor(() => expect(confirmButton).toBeEnabled(), { timeout: 3000 });
  await user.click(confirmButton);
}

describe("Searchbar barcode scanning", () => {
  beforeEach(() => {
    vi.mocked(visitUrl).mockClear();
  });

  test("When the SCAN module is allowed, the scan button and scan placeholder are shown.", () => {
    const search = new Search({
      factory: mockFactory(),
    });
    renderSearchbar({ search });
    expect(screen.getByRole("button", { name: "inventory:search.controls.searchbar.scanBarcode" })).toBeVisible();
    expect(screen.getByRole("searchbox")).toHaveAttribute(
      "placeholder",
      "inventory:search.controls.searchbar.scanPlaceholder",
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
    setMockScannedBarcode("BC-1234");
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
    const permalink = `${window.location.origin}/inventory/sample/123`;
    setMockScannedBarcode(permalink);
    const handleSearch = vi.fn<(query: string) => void>();
    const search = new Search({
      factory: mockFactory(),
    });
    renderSearchbar({ search, handleSearch });

    await scanIntoOpenScanner(user);

    expect(vi.mocked(visitUrl)).toHaveBeenCalledWith(permalink);
    expect(handleSearch).not.toHaveBeenCalled();
  });

  test("A cross-origin URL that mimics a permalink is searched, not navigated to.", async () => {
    const user = userEvent.setup();
    vi.spyOn(HTMLVideoElement.prototype, "play").mockImplementation(() => Promise.resolve());
    setMockScannedBarcode("https://evil.example/inventory/sample/123");
    const handleSearch = vi.fn<(query: string) => void>();
    const search = new Search({
      factory: mockFactory(),
    });
    renderSearchbar({ search, handleSearch });

    await scanIntoOpenScanner(user);

    expect(vi.mocked(visitUrl)).not.toHaveBeenCalled();
    expect(handleSearch).toHaveBeenCalledWith("https://evil.example/inventory/sample/123");
  });
});
