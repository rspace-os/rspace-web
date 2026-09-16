import "@/__tests__/__mocks__/muiTransitions";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import type { StoreContainer } from "../../../stores/stores/RootStore";
import { storesContext } from "../../../stores/stores-context";
import materialTheme from "../../../theme";
import CreateNew from "../CreateNew";

vi.mock("../FieldmarkImportDialog", () => ({ default: () => null }));
vi.mock("../PidinstImportDialog", () => ({ default: () => null }));
vi.mock("../../../hooks/api/integrationHelpers", () => ({
  useIntegrationIsAllowedAndEnabled: () => ({ tag: "success", value: false }),
}));

const IMPORT_PIDINST = "inventory:createNew.importPidinst";

function renderCreateNew({ pidinstEnabled }: { pidinstEnabled: boolean }) {
  const stores = {
    authStore: { pidinstEnabled },
    uiStore: {
      sidebarOpen: true,
      confirmDiscardAnyChanges: () => Promise.resolve(true),
    },
    searchStore: {
      createNew: vi.fn(),
      fetcher: { generateNewQuery: () => new URLSearchParams() },
    },
    trackingStore: { trackEvent: vi.fn() },
    importStore: { initializeNewImport: vi.fn() },
  } as unknown as StoreContainer;
  return render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={stores}>
        <CreateNew onClick={() => {}} />
      </storesContext.Provider>
    </ThemeProvider>,
  );
}

describe("CreateNew", () => {
  test("offers the PIDINST import when a PIDINST provider is enabled", async () => {
    const user = userEvent.setup();
    renderCreateNew({ pidinstEnabled: true });

    await user.click(screen.getByRole("button", { name: "common:actions.create" }));

    expect(screen.getByRole("menuitem", { name: IMPORT_PIDINST })).toBeVisible();
    expect(screen.getByRole("menuitem", { name: IMPORT_PIDINST })).toHaveAttribute("aria-haspopup", "dialog");
  });

  test("hides the PIDINST import when no PIDINST provider is enabled", async () => {
    const user = userEvent.setup();
    renderCreateNew({ pidinstEnabled: false });

    await user.click(screen.getByRole("button", { name: "common:actions.create" }));

    expect(screen.getByRole("menu")).toBeVisible();
    expect(screen.getByRole("menuitem", { name: "inventory:createNew.newInstrument" })).toBeVisible();
    expect(screen.queryByRole("menuitem", { name: IMPORT_PIDINST })).not.toBeInTheDocument();
  });
});
