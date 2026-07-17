import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type React from "react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import materialTheme from "@/theme";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/muiTransitions";

import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import RaidIntegrationCard, { type RaidConnectedMessage } from "@/eln/apps/integrations/Raid/RaidIntegrationCard";
import type { IntegrationStates } from "@/eln/apps/useIntegrationsEndpoint";
import appsEn from "@/modules/common/i18n/locales/en-US/apps.json";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import AlertContext, { type Alert } from "@/stores/contexts/Alert";

const mockSaveAppOptions = vi.fn();
const mockDeleteAppOptions = vi.fn();
vi.mock("@/eln/apps/useIntegrationsEndpoint", () => ({
  useIntegrationsEndpoint: () => ({
    saveAppOptions: mockSaveAppOptions,
    deleteAppOptions: mockDeleteAppOptions,
  }),
}));
const broadcastHandlers: Array<(e: MessageEvent<RaidConnectedMessage>) => void> = [];
vi.mock("@/modules/common/hooks/broadcast", () => ({
  useBroadcastChannel: (_channel: string, handler: (e: MessageEvent<RaidConnectedMessage>) => void) => {
    broadcastHandlers.push(handler);
  },
}));
const renderWithProviders = (
  integrationState: IntegrationStates["RAID"],
  update: (s: IntegrationStates["RAID"]) => void = () => {},
  RealI18nWrapper?: React.ComponentType<{ children: React.ReactNode }>,
) => {
  const addAlert = vi.fn();
  const removeAlert = vi.fn();
  const result = {
    addAlert,
    removeAlert,
  };
  const tree = (
    <ThemeProvider theme={materialTheme}>
      <AlertContext.Provider value={result}>
        <RaidIntegrationCard integrationState={integrationState} update={update} />
      </AlertContext.Provider>
    </ThemeProvider>
  );
  const view = render(RealI18nWrapper ? <RealI18nWrapper>{tree}</RealI18nWrapper> : tree);
  return { ...view, addAlert };
};
const renderWithRealI18n = async (
  integrationState: IntegrationStates["RAID"],
  update: (s: IntegrationStates["RAID"]) => void = () => {},
) =>
  renderWithProviders(
    integrationState,
    update,
    await createRealI18nWrapper({ resources: { apps: appsEn, common: commonEn }, defaultNS: "apps" }),
  );
const openCard = (name = "apps:integrations.raid.name") => screen.getByRole("button", { name });
const connectButton = (name = "apps:actions.connect") => screen.getByRole("button", { name });
const disconnectButton = (name = "apps:actions.disconnect") => screen.getByRole("button", { name });

function mockDisconnect(response: () => Response) {
  const requests: Request[] = [];
  server.use(
    http.delete("/apps/raid/connect/:alias", ({ request }) => {
      requests.push(request);
      return response();
    }),
  );
  return requests;
}

describe("RaidIntegrationCard", () => {
  let restoreConsole = () => {};
  beforeEach(() => {
    vi.clearAllMocks();
    restoreConsole = silenceConsole(["log"], ["RaidIntegrationCard:"]);
  });
  afterEach(() => {
    restoreConsole();
  });
  describe("Accessibility", () => {
    test("Should have no axe violations once dialog opened.", async () => {
      const { baseElement } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [],
          authenticatedServers: [],
        },
      });

      await userEvent.click(openCard());
      expect(await screen.findByRole("dialog")).toBeVisible();

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });
  describe("Rendering", () => {
    test("Shows placeholder text when there are no authenticated servers.", async () => {
      renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [],
          authenticatedServers: [],
        },
      });

      await userEvent.click(openCard());
      expect(screen.getByText("apps:integrations.raid.noServers")).toBeVisible();
    });

    test("Renders server alias and URL link.", async () => {
      renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [
            { alias: "srvA", url: "https://a.example" },
            { alias: "srvB", url: "https://b.example" },
          ],
          authenticatedServers: [{ alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" }],
        },
      });

      await userEvent.click(openCard());
      expect(screen.getByText("srvA")).toBeVisible();
      expect(screen.getByRole("link", { name: "https://a.example" })).toBeVisible();
      expect(connectButton()).toBeVisible();
    });
  });
  describe("Broadcast connect flow", () => {
    test("Marks server authenticated and shows alert when RAID_CONNECTED message received.", async () => {
      const { addAlert } = await renderWithRealI18n({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [{ alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" }],
        },
      });

      await userEvent.click(openCard("RAiD"));

      expect(connectButton("Connect")).toBeVisible();
      act(() => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        broadcastHandlers.forEach((h) =>
          h({ data: { type: "RAID_CONNECTED", alias: "srvA" } } as MessageEvent<RaidConnectedMessage>),
        );
      });
      expect(disconnectButton("Disconnect")).toBeVisible();
      expect(addAlert).toHaveBeenCalled();
      const alertArg = (vi.mocked(addAlert).mock.calls[0] as Alert[])[0];
      expect(alertArg.message).toBe("Successfully connected to srvA RAiD server.");
    });

    test("Ignores broadcast messages for unknown serverAlias", async () => {
      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [{ alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" }],
        },
      });

      await userEvent.click(openCard());

      expect(connectButton()).toBeVisible();
      act(() => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        broadcastHandlers.forEach((h) =>
          h({ data: { type: "RAID_CONNECTED", alias: "srvB" } } as MessageEvent<RaidConnectedMessage>),
        );
      });
      expect(screen.queryByRole("button", { name: "apps:actions.disconnect" })).not.toBeInTheDocument();
      expect(addAlert).not.toHaveBeenCalled();
    });

    test("Ignores malformed broadcast messages", async () => {
      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [{ alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" }],
        },
      });

      await userEvent.click(openCard());

      expect(connectButton()).toBeVisible();
      act(() => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        broadcastHandlers.forEach((h) => h("aaaaaaa" as unknown as MessageEvent<RaidConnectedMessage>));
      });
      expect(screen.queryByRole("button", { name: "apps:actions.disconnect" })).not.toBeInTheDocument();
      expect(addAlert).not.toHaveBeenCalled();
    });
  });
  describe("Add server", () => {
    test("Clicking Add menu item calls saveAppOptions and adds server.", async () => {
      mockSaveAppOptions.mockResolvedValue({
        credentials: {
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" },
            { alias: "srvB", url: "https://b.example", authenticated: false, optionsId: "2" },
          ],
        },
      });
      renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [
            { alias: "srvA", url: "https://a.example" },
            { alias: "srvB", url: "https://b.example" },
          ],
          authenticatedServers: [{ alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" }],
        },
      });

      await userEvent.click(openCard());
      await userEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      const menuItem = await screen.findByRole("menuitem", { name: "srvB https://b.example" });

      await userEvent.click(menuItem);
      await waitFor(() => {
        expect(mockSaveAppOptions).toHaveBeenCalled();
      });
      expect(screen.getByText("srvB")).toBeVisible();
    });
  });
  describe("Disconnect", () => {
    test("Disconnect button triggers fetch and shows success alert.", async () => {
      const requests = mockDisconnect(() => HttpResponse.json({ ok: true }));
      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [{ alias: "srvA", url: "https://a.example", authenticated: true, optionsId: "1" }],
        },
      });

      await userEvent.click(openCard());
      const disconnectBtn = disconnectButton();

      await userEvent.click(disconnectBtn);
      await waitFor(() => {
        expect(requests).toHaveLength(1);
      });
      expect(new URL(requests[0].url).pathname).toBe("/apps/raid/connect/srvA");
      expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
      await waitFor(() => {
        expect(addAlert).toHaveBeenCalled();
      });
      const alertArg = (addAlert.mock.calls[0] as Alert[])[0];
      expect(alertArg.message).toContain("apps:integrations.raid.alerts.disconnectSuccess");
      expect(connectButton()).toBeVisible();
    });

    test("Disconnect button shows error alert on fetch failure.", async () => {
      const requests = mockDisconnect(
        () => new HttpResponse(null, { status: 500, statusText: "Internal Server Error" }),
      );
      const { addAlert } = await renderWithRealI18n({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [{ alias: "srvA", url: "https://a.example", authenticated: true, optionsId: "1" }],
        },
      });

      await userEvent.click(openCard("RAiD"));
      const disconnectBtn = disconnectButton("Disconnect");

      await userEvent.click(disconnectBtn);
      await waitFor(() => {
        expect(requests).toHaveLength(1);
      });
      expect(new URL(requests[0].url).pathname).toBe("/apps/raid/connect/srvA");
      expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
      await waitFor(() => {
        expect(addAlert).toHaveBeenCalled();
      });
      const alertArg = (addAlert.mock.calls[0] as Alert[])[0];
      expect(alertArg.title).toBe("Could not disconnect srvA RAiD connection");
      expect(alertArg.message).toBe("Server responded with status 500: Internal Server Error");
      expect(disconnectButton("Disconnect")).toBeVisible();
    });

    test("Disconnect button shows error alert on fetch network error.", async () => {
      const requests = mockDisconnect(() => HttpResponse.error());
      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [{ alias: "srvA", url: "https://a.example", authenticated: true, optionsId: "1" }],
        },
      });

      await userEvent.click(openCard());
      const disconnectBtn = disconnectButton();

      await userEvent.click(disconnectBtn);
      await waitFor(() => {
        expect(requests).toHaveLength(1);
      });
      expect(new URL(requests[0].url).pathname).toBe("/apps/raid/connect/srvA");
      expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
      await waitFor(() => {
        expect(addAlert).toHaveBeenCalled();
      });
      const alertArg = (addAlert.mock.calls[0] as Alert[])[0];
      expect(alertArg).toMatchObject({
        variant: "error",
        title: "apps:integrations.raid.alerts.disconnectError",
      });
      expect(disconnectButton()).toBeVisible();
    });
  });
  describe("Delete", () => {
    test("Delete button calls deleteAppOptions and removes server from list.", async () => {
      mockDeleteAppOptions.mockResolvedValue({});
      renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [{ alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" }],
        },
      });

      await userEvent.click(openCard());
      const deleteBtn = screen.getByRole("button", { name: "common:actions.delete" });

      await userEvent.click(deleteBtn);
      await waitFor(() => {
        expect(mockDeleteAppOptions).toHaveBeenCalledWith("RAID", "1");
      });
    });
  });
});
