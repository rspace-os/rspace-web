import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import type { IntegrationStates } from "@/eln/apps/useIntegrationsEndpoint";
import { Optional } from "@/util/optional";
import { renderWithIntegrations } from "../../__tests__/integrationsQueryClient";
import Galaxy from "../Galaxy";
import "@/__tests__/__mocks__/matchMedia";

const mockSaveAppOptions = vi.fn();
const mockDeleteAppOptions = vi.fn();
const mockFetchAllIntegrations = vi.fn();
vi.mock("@/eln/apps/useIntegrationsEndpoint", () => ({
  useIntegrationsEndpoint: () => ({ saveAppOptions: mockSaveAppOptions, deleteAppOptions: mockDeleteAppOptions }),
  fetchAllIntegrations: () => mockFetchAllIntegrations(),
}));

const state: IntegrationStates["GALAXY"] = {
  mode: "DISABLED",
  credentials: {
    configuredServers: [],
    authenticatedServers: [{ alias: "main", url: "https://galaxy.example", apiKey: null, optionsId: "1" }],
  },
};

const withApiKey = (apiKey: string | null): IntegrationStates["GALAXY"] => ({
  ...state,
  credentials: {
    ...state.credentials,
    authenticatedServers: [{ ...state.credentials.authenticatedServers[0], apiKey }],
  },
});

describe("Galaxy", () => {
  test("shows a new API key as unchanged once it is saved", async () => {
    const user = userEvent.setup();
    // the server returns a stored key as null
    mockSaveAppOptions.mockReset().mockResolvedValue(withApiKey(null));
    renderWithIntegrations(<Galaxy />, { GALAXY: state });
    await user.click(screen.getByRole("button", { name: "apps:integrations.galaxy.name" }));
    const apiKeyField = screen.getByLabelText("apps:integrations.galaxy.apiKeyLabel");

    await user.type(apiKeyField, "new-key");
    await user.click(screen.getByRole("button", { name: "common:actions.save" }));

    expect(mockSaveAppOptions).toHaveBeenCalledWith("GALAXY", Optional.present("1"), {
      GALAXY_ALIAS: "main",
      GALAXY_URL: "https://galaxy.example",
      GALAXY_APIKEY: "new-key",
    });
    await waitFor(() => {
      expect(apiKeyField).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
    });
    expect(apiKeyField).toHaveValue("");
  });

  test("locks a row while clearing its saved API key is pending", async () => {
    const user = userEvent.setup();
    let finishSave!: () => void;
    const pendingSave = new Promise<IntegrationStates["GALAXY"]>((resolve) => {
      finishSave = () => resolve(withApiKey(""));
    });
    mockSaveAppOptions.mockReset().mockReturnValue(pendingSave);
    renderWithIntegrations(<Galaxy />, { GALAXY: state });
    await user.click(screen.getByRole("button", { name: "apps:integrations.galaxy.name" }));
    const apiKeyField = screen.getByLabelText("apps:integrations.galaxy.apiKeyLabel");

    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.clear" }));
    await user.click(screen.getByRole("button", { name: "common:actions.save" }));
    await waitFor(() =>
      expect(mockSaveAppOptions).toHaveBeenCalledWith(
        "GALAXY",
        Optional.present("1"),
        expect.objectContaining({ GALAXY_APIKEY: "" }),
      ),
    );

    expect(apiKeyField).toBeDisabled();
    expect(screen.getByRole("button", { name: "common:actions.save" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "common:actions.delete" })).toBeDisabled();
    await user.type(apiKeyField, "replacement");
    expect(apiKeyField).toHaveValue("");

    finishSave();
    await waitFor(() => expect(apiKeyField).not.toBeDisabled());
    expect(screen.queryByRole("button", { name: "common:inputs.secretField.undoClear" })).not.toBeInTheDocument();
    expect(apiKeyField).toHaveValue("");
  });

  test("keeps each server's id when two additions are pending at once and blocks a repeat", async () => {
    const user = userEvent.setup();
    const servers = { a: { alias: "a", url: "https://a.example" }, b: { alias: "b", url: "https://b.example" } };
    const saved = (alias: string, optionsId: string) => ({ ...servers[alias as "a" | "b"], apiKey: "", optionsId });
    const galaxy = (authenticatedServers: ReturnType<typeof saved>[]): IntegrationStates["GALAXY"] => ({
      mode: "DISABLED",
      credentials: { configuredServers: [servers.a, servers.b], authenticatedServers },
    });
    let finishA!: () => void;
    let finishB!: () => void;
    mockSaveAppOptions
      .mockReset()
      .mockReturnValueOnce(
        new Promise((resolve) => {
          finishA = () => resolve(galaxy([saved("a", "10")]));
        }),
      )
      .mockReturnValueOnce(
        new Promise((resolve) => {
          finishB = () => resolve(galaxy([saved("a", "10"), saved("b", "11")]));
        }),
      );
    // the two adds overlapped, so once both have settled the integrations are refetched to reconcile them
    mockFetchAllIntegrations.mockReset().mockResolvedValue({ GALAXY: galaxy([saved("a", "10"), saved("b", "11")]) });
    mockDeleteAppOptions.mockReset().mockResolvedValue(undefined);
    renderWithIntegrations(<Galaxy />, { GALAXY: galaxy([]) });
    await user.click(screen.getByRole("button", { name: "apps:integrations.galaxy.name" }));

    await user.click(screen.getByRole("button", { name: "common:actions.add" }));
    await user.click(await screen.findByRole("menuitem", { name: /https:\/\/a\.example/ }));
    expect(screen.getByRole("menuitem", { name: /https:\/\/a\.example/ })).toHaveAttribute("aria-disabled", "true");
    await user.click(screen.getByRole("menuitem", { name: /https:\/\/b\.example/ }));
    finishA();
    // A is shown at once, but its add settles only after the overlap is reconciled, so the menu (which hides the
    // rest of the dialog from the accessibility tree) stays open until then
    await waitFor(() =>
      expect(screen.getAllByRole("button", { name: "common:actions.delete", hidden: true })).toHaveLength(1),
    );
    finishB();
    await waitFor(() => expect(screen.getAllByRole("button", { name: "common:actions.delete" })).toHaveLength(2));

    await user.click(screen.getAllByRole("button", { name: "common:actions.delete" })[1]);
    expect(mockDeleteAppOptions).toHaveBeenCalledWith("GALAXY", "11");
    expect(mockSaveAppOptions).toHaveBeenCalledTimes(2);
    expect(mockFetchAllIntegrations).toHaveBeenCalledOnce();
  });
});
