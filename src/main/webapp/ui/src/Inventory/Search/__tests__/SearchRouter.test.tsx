/*
 * @vitest-environment jsdom
 */

import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { useContext } from "react";
import { MemoryRouter, useLocation, useNavigate } from "react-router";
import { afterEach, describe, expect, test, vi } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import NavigateContext from "../../../stores/contexts/Navigate";
import SearchRouter from "../SearchRouter";

const setupAndPerformInitialSearch = vi.fn(() => Promise.resolve());

vi.mock("../../../stores/use-stores", () => ({
  default: () => ({
    searchStore: {
      search: {
        setupAndPerformInitialSearch,
        activeResult: null,
        filteredResults: [],
        fetcher: { generateQuery: () => new URLSearchParams() },
      },
    },
    uiStore: {
      addAlert: vi.fn(),
      setVisiblePanel: vi.fn(),
      isSmall: false,
      isVerySmall: false,
    },
  }),
}));

vi.mock("../../../hooks/api/useUiPreference", () => ({
  UiPreferences: ({ children }: { children: React.ReactNode }) => children,
}));

// Navigates through MainSearchNavigationContext, as the real header's search box does.
vi.mock("../../components/Layout/Header", () => ({
  default: function MockHeader() {
    const { useNavigate: useContextNavigate } = useContext(NavigateContext);
    const navigate = useContextNavigate();
    return (
      <>
        <button type="button" onClick={() => navigate("/inventory/search?resultType=ALL")}>
          {"go to ALL"}
        </button>
        <button type="button" onClick={() => navigate("/inventory/search?resultType=SAMPLE#details")}>
          {"go to SAMPLE with fragment"}
        </button>
      </>
    );
  },
}));
vi.mock("../../components/Layout/Sidebar", () => ({ default: () => null }));
vi.mock("../../components/Layout/Layout2x1", () => ({ default: () => <div data-testid="listing" /> }));
vi.mock("../LeftPanelView", () => ({ default: () => null }));
vi.mock("../RightPanelView", () => ({ default: () => null }));

function RouterNavigateContext({ children }: { children: React.ReactNode }) {
  return (
    <NavigateContext.Provider
      value={{
        useNavigate: () => {
          const navigate = useNavigate();
          return (url: string) => {
            void navigate(url);
          };
        },
        useLocation,
      }}
    >
      {children}
    </NavigateContext.Provider>
  );
}

function renderAt(search: string) {
  return renderWithRealI18n(
    <MemoryRouter initialEntries={[`/inventory/search${search}`]}>
      <RouterNavigateContext>
        <SearchRouter />
      </RouterNavigateContext>
    </MemoryRouter>,
    { resources: { inventory: inventoryEn }, defaultNS: "inventory" },
  );
}

describe("SearchRouter", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  test.each([
    ["?resultType=ALL", "ALL"],
    ["?resultType=FOO", "FOO"],
    ["?resultType=ALL&query=test", "ALL"],
  ])("%s shows an error instead of searching", async (search, value) => {
    await renderAt(search);

    const alert = screen.getByRole("alert");
    expect(alert).toMatchTextContent("Invalid search link");
    expect(alert).toMatchTextContent(`This link asks for the result type "${value}", which Inventory cannot list.`);
    expect(screen.queryByTestId("listing")).not.toBeInTheDocument();
    expect(setupAndPerformInitialSearch).not.toHaveBeenCalled();
  });

  test.each([
    ["", {}],
    ["?resultType=", {}],
    ["?resultType=CONTAINER", { resultType: "CONTAINER" }],
    ["?resultType=SAMPLE", { resultType: "SAMPLE" }],
    ["?query=test", { query: "test" }],
  ])("'%s' shows the listing", async (search, params) => {
    await renderAt(search);

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByTestId("listing")).toBeInTheDocument();
    expect(setupAndPerformInitialSearch).toHaveBeenCalledTimes(1);
    expect(setupAndPerformInitialSearch).toHaveBeenCalledWith(params);
  });

  test("in-app navigation to ALL shows the error without searching, and the recovery link searches again", async () => {
    const user = userEvent.setup();
    await renderAt("?resultType=SAMPLE");
    expect(setupAndPerformInitialSearch).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "go to ALL" }));
    expect(screen.getByRole("alert")).toMatchTextContent('the result type "ALL"');
    expect(screen.queryByTestId("listing")).not.toBeInTheDocument();
    expect(setupAndPerformInitialSearch).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("link", { name: "Go to Inventory search" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByTestId("listing")).toBeInTheDocument();
    expect(setupAndPerformInitialSearch).toHaveBeenCalledTimes(2);
    expect(setupAndPerformInitialSearch).toHaveBeenLastCalledWith({});
  });

  test("in-app navigation ignores the URL fragment when searching", async () => {
    const user = userEvent.setup();
    await renderAt("");
    expect(setupAndPerformInitialSearch).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "go to SAMPLE with fragment" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(setupAndPerformInitialSearch).toHaveBeenCalledTimes(2);
    expect(setupAndPerformInitialSearch).toHaveBeenLastCalledWith({ resultType: "SAMPLE" });
  });
});
