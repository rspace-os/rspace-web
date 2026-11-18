import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { runInAction } from "mobx";
import { HttpResponse, http } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { DeploymentPropertyContext } from "../../../../hooks/api/useDeploymentProperty";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import type { ChemicalFilter } from "../../../../stores/definitions/Search";
import Search, { DEFAULT_SEARCH_MODULES } from "../../../../stores/models/Search";
import materialTheme from "../../../../theme";
import { ChemicalFilterButton, ChemicalFilterChip } from "../ChemicalFilter";
import "@/__tests__/__mocks__/matchMedia";
import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

// the Ketcher editor is exercised end to end; here only the dialog's contract matters
vi.mock("../ChemicalStructureDialog", () => ({
  default: ({
    open,
    initialFilter,
    onSubmit,
  }: {
    open: boolean;
    initialFilter: ChemicalFilter | null;
    onSubmit: (filter: ChemicalFilter) => void;
  }) =>
    open ? (
      <div role="dialog" aria-label="Structure editor">
        <span>{`Editing ${initialFilter?.chemQuery ?? "nothing"}`}</span>
        <button type="button" onClick={() => onSubmit({ chemQuery: "CCO", chemSearchType: "EXACT" })}>
          {"Submit structure"}
        </button>
      </div>
    ) : null,
}));

const mockChemistryApp = (enabled: boolean) => {
  const requests: Array<string> = [];
  server.use(
    http.get("*/integration/integrationInfo", ({ request }) => {
      requests.push(new URL(request.url).searchParams.get("name") ?? "");
      return HttpResponse.json({
        data: {
          name: "CHEMISTRY",
          displayName: "Chemistry",
          available: true,
          enabled,
          oauthConnected: false,
          options: {},
        },
      });
    }),
  );
  return requests;
};

const mainSearch = () => {
  const search = new Search({
    factory: mockFactory(),
    uiConfig: { allowedSearchModules: new Set([...DEFAULT_SEARCH_MODULES, "CHEMISTRY"]) },
  });
  const overrideSearchOnFilter = vi.fn();
  runInAction(() => {
    search.overrideSearchOnFilter = overrideSearchOnFilter;
  });
  return { search, overrideSearchOnFilter };
};

const renderWithSearch = (search: Search, ui: React.ReactNode, provider = "indigo") =>
  render(
    <ThemeProvider theme={materialTheme}>
      <DeploymentPropertyContext.Provider value={new Map([["chemistry.provider", provider]])}>
        <SearchContext.Provider value={{ search, differentSearchForSettingActiveResult: search }}>
          {ui}
        </SearchContext.Provider>
      </DeploymentPropertyContext.Provider>
    </ThemeProvider>,
  );

describe("Chemical filter button", () => {
  test("opens the structure editor and applies the drawn structure", async () => {
    const user = userEvent.setup();
    mockChemistryApp(true);
    const { search, overrideSearchOnFilter } = mainSearch();
    renderWithSearch(search, <ChemicalFilterButton />);

    await user.click(await screen.findByRole("button", { name: "inventory:search.controls.chemical.label" }));
    await user.click(screen.getByRole("button", { name: "Submit structure" }));

    expect(search.fetcher.chemQuery).toBe("CCO");
    expect(search.fetcher.chemSearchType).toBe("EXACT");
    expect(overrideSearchOnFilter).toHaveBeenCalled();
    expect(screen.queryByRole("dialog", { name: "Structure editor" })).not.toBeInTheDocument();
  });

  test("is not offered when the user has not enabled the Chemistry app", async () => {
    const requests = mockChemistryApp(false);
    const { search } = mainSearch();
    renderWithSearch(search, <ChemicalFilterButton />);

    await waitFor(() => expect(requests).toEqual(["CHEMISTRY"]));
    expect(screen.queryByRole("button", { name: "inventory:search.controls.chemical.label" })).not.toBeInTheDocument();
  });

  test("is not offered without the Indigo chemistry service", async () => {
    const requests = mockChemistryApp(true);
    const { search } = mainSearch();
    renderWithSearch(search, <ChemicalFilterButton />, "");

    await waitFor(() => expect(requests).toEqual(["CHEMISTRY"]));
    expect(screen.queryByRole("button", { name: "inventory:search.controls.chemical.label" })).not.toBeInTheDocument();
  });

  test("is disabled, with the reason as a tooltip, while the search is scoped to a container", async () => {
    const user = userEvent.setup();
    mockChemistryApp(true);
    const { search } = mainSearch();
    search.setParentGlobalId("IC1", false);
    renderWithSearch(search, <ChemicalFilterButton />);

    const button = await screen.findByRole("button", { name: "inventory:search.controls.chemical.label" });
    expect(button).toBeDisabled();
    await user.hover(button.parentElement as HTMLElement);
    expect(await screen.findByText("inventory:search.controls.chemical.blockedByScope")).toBeVisible();
  });
});

test("Chemical filter button stays enabled on a bench, and applying a structure leaves the bench", async () => {
  const user = userEvent.setup();
  mockChemistryApp(true);
  const { search } = mainSearch();
  search.setParentGlobalId("BE1", false);
  renderWithSearch(search, <ChemicalFilterButton />);

  await user.click(await screen.findByRole("button", { name: "inventory:search.controls.chemical.label" }));
  await user.click(screen.getByRole("button", { name: "Submit structure" }));

  expect(search.fetcher.parentGlobalId).toBeNull();
  expect(search.fetcher.chemQuery).toBe("CCO");
});

describe("Chemical filter chip", () => {
  test("shows the match type and structure, and removing it clears the filter", async () => {
    const user = userEvent.setup();
    mockChemistryApp(true);
    const { search } = mainSearch();
    search.setChemicalFilter({ chemQuery: "c1ccccc1", chemSearchType: "SUBSTRUCTURE" }, false);
    renderWithSearch(search, <ChemicalFilterChip />);

    expect(screen.getByText("inventory:search.parameterChips.chemical")).toBeVisible();
    await user.click(screen.getByTestId("CancelIcon"));

    expect(search.fetcher.chemQuery).toBeNull();
  });

  test("reopens the editor with the current structure loaded", async () => {
    const user = userEvent.setup();
    mockChemistryApp(true);
    const { search } = mainSearch();
    search.setChemicalFilter({ chemQuery: "c1ccccc1", chemSearchType: "EXACT" }, false);
    renderWithSearch(search, <ChemicalFilterChip />);

    await user.click(await screen.findByRole("button", { name: "inventory:search.parameterChips.chemicalEdit" }));

    expect(
      within(screen.getByRole("dialog", { name: "Structure editor" })).getByText("Editing c1ccccc1"),
    ).toBeVisible();
  });

  test("turns to an error state when the structure search fails", () => {
    mockChemistryApp(true);
    const { search } = mainSearch();
    search.setChemicalFilter({ chemQuery: "c1ccccc1", chemSearchType: "SUBSTRUCTURE" }, false);
    runInAction(() => {
      search.fetcher.error = "Structure search is not available";
    });
    renderWithSearch(search, <ChemicalFilterChip />);

    expect(screen.getByText("inventory:search.parameterChips.chemical").closest(".MuiChip-root")).toHaveClass(
      "MuiChip-colorError",
    );
  });
});

describe("Chemical filter chip label", () => {
  setupRealAppI18n();

  test.each([
    ["c1ccccc1", "SUBSTRUCTURE" as const, "Substructure: c1ccccc1"],
    ["c1ccccc1", "EXACT" as const, "Exact: c1ccccc1"],
    ["C".repeat(40), "SUBSTRUCTURE" as const, `Substructure: ${"C".repeat(29)}…`],
  ])("shows %s (%s) as %s", (chemQuery, chemSearchType, label) => {
    mockChemistryApp(true);
    const { search } = mainSearch();
    search.setChemicalFilter({ chemQuery, chemSearchType }, false);
    renderWithSearch(search, <ChemicalFilterChip />);

    expect(screen.getByText(label)).toBeVisible();
  });
});
