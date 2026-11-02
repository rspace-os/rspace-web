import { ThemeProvider } from "@mui/material/styles";
import { screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import { containerAttrs, makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import Search from "../../../../stores/models/Search";
import materialTheme from "../../../../theme";
import NavigateToNode from "../NavigateToNode";

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    searchStore: { search: {} },
    uiStore: { addAlert: () => {}, isVerySmall: false },
    authStore: { isSynchronizing: false },
    moveStore: {
      isMoving: false,
      selectedResultsIncludesContainers: false,
      selectedResultsIncludesSubSamples: false,
      selectedResults: [],
    },
    peopleStore: { currentUser: { username: "user" } },
  }),
}));

async function renderNavigateToNode(search: Search) {
  const container = makeMockContainer({ parentContainers: [containerAttrs({ globalId: "IC2" })] });
  container.contentSearch.cacheFetcher.results = [makeMockContainer({ globalId: "IC3" })];
  await renderWithRealI18n(
    <ThemeProvider theme={materialTheme}>
      <SearchContext.Provider value={{ search, differentSearchForSettingActiveResult: search }}>
        <NavigateToNode node={container} />
      </SearchContext.Provider>
    </ThemeProvider>,
    { resources: { common: commonEn, inventory: inventoryEn }, defaultNS: "inventory" },
  );
}

describe("NavigateToNode", () => {
  test("offers to navigate into a container that has contents", async () => {
    await renderNavigateToNode(new Search({ factory: mockFactory() }));
    expect(screen.getByRole("button", { name: "Navigate to container" })).toBeInTheDocument();
  });

  test("is hidden when the search is for picking one container and hides navigation into its contents", async () => {
    await renderNavigateToNode(new Search({ factory: mockFactory(), uiConfig: { hideNavigateToContents: true } }));
    expect(screen.queryByRole("button", { name: "Navigate to container" })).not.toBeInTheDocument();
  });
});
