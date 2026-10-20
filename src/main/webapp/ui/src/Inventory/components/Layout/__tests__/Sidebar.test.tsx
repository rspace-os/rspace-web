import { beforeEach, describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import type { ReactElement, ReactNode } from "react";
import axios from "@/common/axios";
import { FEATURE_FLAGS } from "@/featureFlags/generatedFeatureFlags";
import { useIsFeatureFlagEnabled } from "@/featureFlags/queries";
import { LandmarksProvider } from "../../../../components/LandmarksContext";
import NavigateContext from "../../../../stores/contexts/Navigate";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import Sidebar from "../Sidebar";

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

vi.mock("../../../../hooks/api/integrationHelpers", () => ({
  useIntegrationIsAllowedAndEnabled: () => ({
    tag: "success",
    value: false,
  }),
}));
vi.mock("@/featureFlags/queries", () => ({
  useIsFeatureFlagEnabled: vi.fn(),
}));

// Defaults to DENIED so the two pre-existing tests below are unaffected; the Requests-visibility
// tests further down set this explicitly for each case.
const deploymentProperties: Record<string, string> = {
  "inventory.sampleRequests.available": "DENIED",
};
vi.mock("../../../../hooks/api/useDeploymentProperty", () => ({
  useDeploymentProperty: (name: string) => ({ tag: "success", value: deploymentProperties[name] }),
}));

const mockAxios = new MockAdapter(axios);

function renderSidebar(rootStore: ReturnType<typeof makeMockRootStore>, content: ReactNode = <Sidebar id="foo" />) {
  return renderWithProviders(
    <ThemeProvider theme={materialTheme}>
      <LandmarksProvider>
        <storesContext.Provider value={rootStore}>{content}</storesContext.Provider>
      </LandmarksProvider>
    </ThemeProvider>,
  );
}

describe("Sidebar", () => {
  beforeEach(() => {
    mockAxios.reset();
    deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
    vi.mocked(useIsFeatureFlagEnabled).mockReturnValue(true);
  });

  test("Booking System links to Booking when enabled", () => {
    const rootStore = makeMockRootStore({
      uiStore: { alwaysVisibleSidebar: true, sidebarOpen: true },
      searchStore: { search: { benchSearch: true } },
    });
    renderSidebar(rootStore);

    expect(useIsFeatureFlagEnabled).toHaveBeenCalledWith(FEATURE_FLAGS.bookingEnabled);
    expect(screen.getByRole("link", { name: "inventory:layout.sidebar.bookingSystem" })).toHaveAttribute(
      "href",
      "/booking",
    );
  });

  test("Booking System is hidden when the feature is disabled", () => {
    vi.mocked(useIsFeatureFlagEnabled).mockReturnValue(false);
    const rootStore = makeMockRootStore({
      uiStore: { alwaysVisibleSidebar: true, sidebarOpen: true },
      searchStore: { search: { benchSearch: true } },
    });
    renderSidebar(rootStore);

    expect(screen.queryByRole("link", { name: "inventory:layout.sidebar.bookingSystem" })).not.toBeInTheDocument();
  });

  test("Should have no axe violations.", async () => {
    mockAxios.onGet("livechatProperties").reply(200, {
      livechatEnabled: false,
    });
    const rootStore = makeMockRootStore({
      uiStore: {
        alwaysVisibleSidebar: true,
        sidebarOpen: true,
      },
      searchStore: {
        search: {
          benchSearch: true,
        },
      },
    });
    const { container } = renderSidebar(rootStore);

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(container).toBeAccessible();
  });

  // Forcing the right panel visible on nav clicks left stale record details on screen in single-column layouts.
  test("Clicking a record type nav item should not change the visible panel.", async () => {
    mockAxios.onGet("livechatProperties").reply(200, {
      livechatEnabled: false,
    });
    const user = userEvent.setup();
    const navFn =
      vi.fn<(url: string, opts?: { skipToParentContext?: boolean; modifyVisiblePanel?: boolean }) => void>();
    const setVisiblePanel = vi.fn<(panel: "left" | "right") => void>();
    const rootStore = makeMockRootStore({
      uiStore: {
        alwaysVisibleSidebar: true,
        sidebarOpen: true,
        isVerySmall: false,
        setVisiblePanel,
      },
      searchStore: {
        search: {
          benchSearch: true,
        },
        fetcher: {
          generateNewQuery: () => new URLSearchParams({ resultType: "INSTRUMENT" }),
        },
      },
    });
    renderSidebar(
      rootStore,
      <NavigateContext.Provider
        value={{
          useNavigate: () => navFn,
          useLocation: () => ({
            hash: "",
            pathname: "",
            search: "",
            state: {},
            key: "",
          }),
        }}
      >
        <Sidebar id="foo" />
      </NavigateContext.Provider>,
    );

    await user.click(screen.getByRole("button", { name: "inventory:recordTypes.instrument.plural" }));

    expect(navFn).toHaveBeenCalledTimes(1);
    const [url] = navFn.mock.calls[0];
    expect(url).toMatch(/^\/inventory\/search\?/);
    expect(setVisiblePanel).not.toHaveBeenCalled();
  });

  function renderDefaultSidebar() {
    mockAxios.onGet("livechatProperties").reply(200, { livechatEnabled: false });
    return renderSidebar(
      makeMockRootStore({
        uiStore: { alwaysVisibleSidebar: true, sidebarOpen: true },
        searchStore: { search: { benchSearch: true } },
      }),
    );
  }

  test("hides the Requests nav item when inventory.sampleRequests.available is not ALLOWED.", () => {
    deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
    renderDefaultSidebar();

    expect(screen.queryByRole("button", { name: "inventory:layout.sidebar.requests" })).toBeNull();
  });

  test("shows the Requests nav item when inventory.sampleRequests.available is ALLOWED.", () => {
    deploymentProperties["inventory.sampleRequests.available"] = "ALLOWED";
    renderDefaultSidebar();

    expect(screen.getByRole("button", { name: "inventory:layout.sidebar.requests" })).toBeInTheDocument();
  });
});
