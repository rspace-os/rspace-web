import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { LandmarksProvider } from "../../../../components/LandmarksContext";
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
});
