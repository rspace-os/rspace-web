import "@/__tests__/__mocks__/muiTransitions";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import NavigateContext from "../../../stores/contexts/Navigate";
import type { StoreContainer } from "../../../stores/stores/RootStore";
import { storesContext } from "../../../stores/stores-context";
import materialTheme from "../../../theme";
import CreateNew from "../CreateNew";

vi.mock("../FieldmarkImportDialog", () => ({ default: () => null }));
/**
 * A stub rather than `() => null`, so the test can see whether the menu item actually opened the
 * dialog and can fire the import callback CreateNew wires up.
 */
vi.mock("../PidinstImportDialog", () => ({
  default: ({
    open,
    onImported,
  }: {
    open: boolean;
    onImported: (instrument: { id: number; globalId: string }) => void;
  }) =>
    open ? (
      <div data-testid="pidinst-dialog">
        <button
          type="button"
          aria-label="stub import"
          onClick={() => {
            onImported({ id: 77, globalId: "IN77" });
          }}
        />
      </div>
    ) : null,
}));
vi.mock("../../../hooks/api/integrationHelpers", () => ({
  useIntegrationIsAllowedAndEnabled: () => ({ tag: "success", value: false }),
}));

const IMPORT_PIDINST = "inventory:createNew.importPidinst";

function renderCreateNew({ pidinstEnabled, navigate = vi.fn() }: { pidinstEnabled: boolean; navigate?: () => void }) {
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
      <NavigateContext.Provider value={{ useNavigate: () => navigate, useLocation: () => window.location as never }}>
        <storesContext.Provider value={stores}>
          <CreateNew onClick={() => {}} />
        </storesContext.Provider>
      </NavigateContext.Provider>
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

  test("opens the import dialog from the menu item and navigates to what it imports", async () => {
    const user = userEvent.setup();
    const navigate = vi.fn();
    renderCreateNew({ pidinstEnabled: true, navigate });

    await user.click(screen.getByRole("button", { name: "common:actions.create" }));
    expect(screen.queryByTestId("pidinst-dialog")).toBeNull();

    await user.click(screen.getByRole("menuitem", { name: IMPORT_PIDINST }));
    expect(screen.getByTestId("pidinst-dialog")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "stub import" }));

    expect(navigate).toHaveBeenCalledWith("/inventory/instrument/77");
  });
});
