import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/muiTransitions";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import SettingsDialog from "../SettingsDialog";

const BASE_DATACITE = {
  enabled: "false" as const,
  serverUrl: "https://api.datacite.org" as const,
  username: "user",
  password: "pass",
  repositoryPrefix: "10.TEST",
};

const BASE_B2INST = {
  enabled: "false" as const,
  serverUrl: "https://b2inst.example.com",
  username: "community",
  password: "token",
  repositoryPrefix: "",
};

function makeAuthStore(
  overrides: {
    pidinstDatacite?: { enabled?: "true" | "false" };
    pidinstB2Inst?: { enabled?: "true" | "false" };
    igsnDatacite?: { enabled?: "true" | "false" };
  } = {},
) {
  return {
    systemSettings: {
      igsnDatacite: { ...BASE_DATACITE, ...overrides.igsnDatacite },
      pidinstDatacite: { ...BASE_DATACITE, ...overrides.pidinstDatacite },
      pidinstB2Inst: { ...BASE_B2INST, ...overrides.pidinstB2Inst },
    },
    getSystemSettings: vi.fn().mockResolvedValue(undefined),
    updateSystemSettings: vi.fn().mockResolvedValue(undefined),
  };
}

async function renderDialog(authStore: ReturnType<typeof makeAuthStore>) {
  const rootStore = makeMockRootStore({ authStore });
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <SettingsDialog open={true} setOpen={() => {}} />
      </storesContext.Provider>
    </ThemeProvider>,
  );
  // Wait for getSystemSettings to resolve and the tab UI to appear
  await screen.findAllByRole("tab");
}

describe("SettingsDialog", () => {
  describe("PIDINST provider selection", () => {
    test("DataCite is selected by default when B2INST is not enabled", async () => {
      await renderDialog(makeAuthStore());

      expect(
        screen.getByRole("radio", {
          name: "inventory:settings.pidinst.providerOptions.datacite",
        }),
      ).toBeChecked();
    });

    test("B2INST is pre-selected when it is the enabled provider", async () => {
      await renderDialog(makeAuthStore({ pidinstB2Inst: { enabled: "true" } }));

      expect(
        screen.getByRole("radio", {
          name: "inventory:settings.pidinst.providerOptions.b2inst",
        }),
      ).toBeChecked();
    });

    test("switching to B2INST radio checks that option", async () => {
      const user = userEvent.setup();
      await renderDialog(makeAuthStore());

      await user.click(
        screen.getByRole("radio", {
          name: "inventory:settings.pidinst.providerOptions.b2inst",
        }),
      );

      expect(
        screen.getByRole("radio", {
          name: "inventory:settings.pidinst.providerOptions.b2inst",
        }),
      ).toBeChecked();
    });
  });

  describe("PIDINST tab status message", () => {
    test("shows 'nothing connected' when no PIDINST provider is enabled", async () => {
      await renderDialog(makeAuthStore());

      expect(screen.getByText("inventory:settings.tabs.pidinst.nothingConnected")).toBeInTheDocument();
    });

    test("shows connected status when DataCite provider is enabled", async () => {
      await renderDialog(makeAuthStore({ pidinstDatacite: { enabled: "true" } }));

      expect(screen.getByText("inventory:settings.tabs.pidinst.connected")).toBeInTheDocument();
    });

    test("shows connected status when B2INST provider is enabled", async () => {
      await renderDialog(makeAuthStore({ pidinstB2Inst: { enabled: "true" } }));

      expect(screen.getByText("inventory:settings.tabs.pidinst.connected")).toBeInTheDocument();
    });
  });

  describe("IGSN tab status message", () => {
    test("shows connected when IGSN DataCite is enabled", async () => {
      await renderDialog(makeAuthStore({ igsnDatacite: { enabled: "true" } }));

      expect(screen.getByText("inventory:settings.tabs.igsn.connected")).toBeInTheDocument();
    });

    test("shows not-connected when IGSN DataCite is not enabled", async () => {
      await renderDialog(makeAuthStore());

      expect(screen.getByText("inventory:settings.tabs.igsn.notConnected")).toBeInTheDocument();
    });
  });

  test("shows conflict warning when both PIDINST providers are enabled", async () => {
    await renderDialog(
      makeAuthStore({
        pidinstDatacite: { enabled: "true" },
        pidinstB2Inst: { enabled: "true" },
      }),
    );

    /*
     * Both provider cards are always mounted (switching via display:none), so
     * the warning text appears in both. We assert at least one instance exists.
     */
    expect(screen.queryAllByText("inventory:settings.pidinst.conflictWarning")).not.toHaveLength(0);
  });
});
