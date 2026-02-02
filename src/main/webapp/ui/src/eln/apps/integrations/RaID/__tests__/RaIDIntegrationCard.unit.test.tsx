import { test, describe, expect, beforeEach, afterEach, vi } from 'vitest';
import React from "react";
import {
  screen,
  waitFor,
  act,
  render,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import materialTheme from "@/theme";
import { ThemeProvider } from "@mui/material/styles";
import "../../../../../../__mocks__/matchMedia";
import AlertContext, { Alert } from "@/stores/contexts/Alert";
import RaIDIntegrationCard, {
  RaIDConnectedMessage,
} from "@/eln/apps/integrations/RaID/RaIDIntegrationCard";
import { IntegrationStates } from "@/eln/apps/useIntegrationsEndpoint";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";


const mockSaveAppOptions = vi.fn();
const mockDeleteAppOptions = vi.fn();

vi.mock("@/eln/apps/useIntegrationsEndpoint", () => ({
  useIntegrationsEndpoint: () => ({
    saveAppOptions: mockSaveAppOptions,
    deleteAppOptions: mockDeleteAppOptions,
  }),
}));

const broadcastHandlers: Array<(e: MessageEvent<RaIDConnectedMessage>) => void> = [];
vi.mock("use-broadcast-channel", () => ({
  useBroadcastChannel: (_channel: string, handler: (e: MessageEvent<RaIDConnectedMessage>) => void) => {
    broadcastHandlers.push(handler);
  },
}));

const renderWithProviders = (
  integrationState: IntegrationStates["RAID"],
  update: (s: IntegrationStates["RAID"]) => void = () => {}
) => {
  const addAlert = vi.fn();
  const removeAlert = vi.fn();
  const result = {
    addAlert,
    removeAlert,
  };
  const view = render(
    <ThemeProvider theme={materialTheme}>
      <AlertContext.Provider value={result}>
        <RaIDIntegrationCard integrationState={integrationState} update={update} />
      </AlertContext.Provider>
    </ThemeProvider>
  );
  return { ...view, addAlert };
};

describe("RaIDIntegrationCard", () => {
  let restoreConsole = () => {};

  beforeEach(() => {
    vi.clearAllMocks();
    restoreConsole = silenceConsole(["log"], ["RaIDIntegrationCard:"]);
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

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
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

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      expect(screen.getAllByText(/No authenticated servers./i)[0]).toBeVisible();
    });

    test("Renders server alias and URL link.", async () => {
      renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [
            { alias: "srvA", url: "https://a.example" },
            { alias: "srvB", url: "https://b.example" },
          ],
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" },
          ],
        },
      });

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      expect(screen.getByText("srvA")).toBeVisible();
      expect(screen.getByRole("link", { name: /https:\/\/a.example/i })).toBeVisible();
      expect(screen.getByRole("button", { name: /connect/i })).toBeVisible();
    });
  });

  describe("Broadcast connect flow", () => {
    test("Marks server authenticated and shows alert when RAID_CONNECTED message received.", async () => {
      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" },
          ],
        },
      });

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      expect(screen.getByRole("button", { name: /connect/i })).toBeVisible();

      act(() => {
        broadcastHandlers.forEach((h) => h({ data: { type: "RAID_CONNECTED", alias: "srvA" } } as MessageEvent<RaIDConnectedMessage>));
      });

      expect(screen.getByRole("button", { name: /disconnect/i })).toBeVisible();
      expect(addAlert).toHaveBeenCalled();
      const alertArg = (vi.mocked(addAlert).mock.calls[0] as Alert[])[0];
      expect(alertArg.message).toContain("Successfully connected to srvA RaID server.");
    });

    test("Ignores broadcast messages for unknown serverAlias", async () => {
      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" },
          ],
        },
      });

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      expect(screen.getByRole("button", { name: /connect/i })).toBeVisible();

      act(() => {
        broadcastHandlers.forEach((h) => h({ data: { type: "RAID_CONNECTED", alias: "srvB" } } as MessageEvent<RaIDConnectedMessage>));
      });

      expect(screen.queryByRole("button", { name: /disconnect/i })).not.toBeInTheDocument();
      expect(addAlert).not.toHaveBeenCalled();
    });

    test("Ignores malformed broadcast messages", async () => {
      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" },
          ],
        },
      });

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      expect(screen.getByRole("button", { name: /connect/i })).toBeVisible();

      act(() => {
        broadcastHandlers.forEach((h) => h('aaaaaaa' as unknown as MessageEvent<RaIDConnectedMessage>));
      });

      expect(screen.queryByRole("button", { name: /disconnect/i })).not.toBeInTheDocument();
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
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" },
          ],
        },
      });

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      await userEvent.click(screen.getByRole("button", { name: /add/i }));
      const menuItem = await screen.findByRole("menuitem", { name: /srvB/i });
      await userEvent.click(menuItem);

      await waitFor(() => {
        expect(mockSaveAppOptions).toHaveBeenCalled();
      });

      expect(screen.getByText("srvB")).toBeVisible();
    });
  });

  describe("Disconnect", () => {
    test("Disconnect button triggers fetch and shows success alert.", async () => {
      fetchMock.mockResponseOnce(JSON.stringify({ ok: true }));
      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: true, optionsId: "1" },
          ],
        },
      });

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      const disconnectBtn = screen.getByRole("button", { name: /disconnect/i });
      await userEvent.click(disconnectBtn);

      await waitFor(() => {
        expect(fetchMock).toHaveBeenCalledWith("/apps/raid/connect/srvA", { method: "DELETE", headers: { "X-Requested-With": "XMLHttpRequest" } });
      });

      await waitFor(() => {
        expect(addAlert).toHaveBeenCalled();
      });
      const alertArg = (addAlert.mock.calls[0] as Alert[])[0];
      expect(alertArg.message).toContain("Successfully disconnected.");
      expect(screen.getByRole("button", { name: /connect/i })).toBeVisible();
    });

    test("Disconnect button shows error alert on fetch failure.", async () => {
      fetchMock.mockResponseOnce("", { status: 500, statusText: "Internal Server Error" });
      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: true, optionsId: "1" },
          ],
        },
      });

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      const disconnectBtn = screen.getByRole("button", { name: /disconnect/i });
      await userEvent.click(disconnectBtn);

      await waitFor(() => {
        expect(fetchMock).toHaveBeenCalledWith("/apps/raid/connect/srvA", { method: "DELETE", headers: { "X-Requested-With": "XMLHttpRequest" } });
      });

      await waitFor(() => {
        expect(addAlert).toHaveBeenCalled();
      });
      const alertArg = (addAlert.mock.calls[0] as Alert[])[0];
      expect(alertArg.message).toContain("Server responded with status 500: Internal Server Error");
      expect(screen.getByRole("button", { name: /disconnect/i })).toBeVisible();
    });

    test("Disconnect button shows error alert on fetch network error.", async () => {
      fetchMock.mockRejectOnce(new Error("Network error"));

      const { addAlert } = renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: true, optionsId: "1" },
          ],
        },
      });

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      const disconnectBtn = screen.getByRole("button", { name: /disconnect/i });
      await userEvent.click(disconnectBtn);

      await waitFor(() => {
        expect(fetchMock).toHaveBeenCalledWith("/apps/raid/connect/srvA", { method: "DELETE", headers: { "X-Requested-With": "XMLHttpRequest" } });
      });

      await waitFor(() => {
        expect(addAlert).toHaveBeenCalled();
      });
      const alertArg = (addAlert.mock.calls[0] as Alert[])[0];
      expect(alertArg.message).toContain("Network error");
      expect(screen.getByRole("button", { name: /disconnect/i })).toBeVisible();
    });
  });

  describe("Delete", () => {
    test("Delete button calls deleteAppOptions and removes server from list.", async () => {
      mockDeleteAppOptions.mockResolvedValue({});

      renderWithProviders({
        mode: "DISABLED",
        credentials: {
          configuredServers: [{ alias: "srvA", url: "https://a.example" }],
          authenticatedServers: [
            { alias: "srvA", url: "https://a.example", authenticated: false, optionsId: "1" },
          ],
        },
      });

      await userEvent.click(screen.getAllByRole("button", { name: /raid/i })[0]);
      const deleteBtn = screen.getByRole("button", { name: /delete/i });
      await userEvent.click(deleteBtn);

      await waitFor(() => {
        expect(mockDeleteAppOptions).toHaveBeenCalledWith("RAID", "1");
      });
    });
  })
});
