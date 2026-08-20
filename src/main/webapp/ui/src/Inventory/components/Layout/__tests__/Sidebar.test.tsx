import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { LandmarksProvider } from "../../../../components/LandmarksContext";
import NavigateContext from "../../../../stores/contexts/Navigate";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import Sidebar from "../Sidebar";

vi.mock("../../../../hooks/api/integrationHelpers", () => ({
  useIntegrationIsAllowedAndEnabled: () => ({
    tag: "success",
    value: false,
  }),
}));

const mockAxios = new MockAdapter(axios);
describe("Sidebar", () => {
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
    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <LandmarksProvider>
          <storesContext.Provider value={rootStore}>
            <Sidebar id="foo" />
          </storesContext.Provider>
        </LandmarksProvider>
      </ThemeProvider>,
    );

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
    render(
      <ThemeProvider theme={materialTheme}>
        <LandmarksProvider>
          <storesContext.Provider value={rootStore}>
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
            </NavigateContext.Provider>
          </storesContext.Provider>
        </LandmarksProvider>
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: "inventory:recordTypes.instrument.plural" }));

    expect(navFn).toHaveBeenCalledWith(expect.stringMatching(/^\/inventory\/search\?/) as string);
    expect(setVisiblePanel).not.toHaveBeenCalled();
  });
});
