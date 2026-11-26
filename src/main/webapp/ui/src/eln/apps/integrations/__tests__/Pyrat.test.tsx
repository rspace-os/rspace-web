import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import type { IntegrationStates } from "@/eln/apps/useIntegrationsEndpoint";
import { Optional } from "@/util/optional";
import { renderWithIntegrations } from "../../__tests__/integrationsQueryClient";
import Pyrat from "../Pyrat";
import "@/__tests__/__mocks__/matchMedia";

const mockSaveAppOptions = vi.fn();
vi.mock("@/eln/apps/useIntegrationsEndpoint", () => ({
  useIntegrationsEndpoint: () => ({ saveAppOptions: mockSaveAppOptions, deleteAppOptions: vi.fn() }),
  fetchAllIntegrations: vi.fn(),
}));

const state: IntegrationStates["PYRAT"] = {
  mode: "DISABLED",
  credentials: {
    configuredServers: [],
    authenticatedServers: [{ alias: "main", url: "https://pyrat.example", apiKey: null, optionsId: "1" }],
  },
};

const withApiKey = (apiKey: string | null): IntegrationStates["PYRAT"] => ({
  ...state,
  credentials: {
    ...state.credentials,
    authenticatedServers: [{ ...state.credentials.authenticatedServers[0], apiKey }],
  },
});

describe("Pyrat", () => {
  test("shows a new API key as unchanged once it is saved", async () => {
    const user = userEvent.setup();
    // the server returns a stored key as null
    mockSaveAppOptions.mockReset().mockResolvedValue(withApiKey(null));
    renderWithIntegrations(<Pyrat />, { PYRAT: state });
    await user.click(screen.getByRole("button", { name: "apps:integrations.pyrat.name" }));
    const apiKeyField = screen.getByLabelText("apps:integrations.pyrat.apiKeyLabel");

    await user.type(apiKeyField, "new-key");
    await user.click(screen.getByRole("button", { name: "common:actions.save" }));

    expect(mockSaveAppOptions).toHaveBeenCalledWith("PYRAT", Optional.present("1"), {
      PYRAT_ALIAS: "main",
      PYRAT_URL: "https://pyrat.example",
      PYRAT_APIKEY: "new-key",
    });
    await waitFor(() => {
      expect(apiKeyField).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
    });
    expect(apiKeyField).toHaveValue("");
  });

  test("locks a row while clearing its saved API key is pending", async () => {
    const user = userEvent.setup();
    let finishSave!: () => void;
    const pendingSave = new Promise<IntegrationStates["PYRAT"]>((resolve) => {
      finishSave = () => resolve(withApiKey(""));
    });
    mockSaveAppOptions.mockReset().mockReturnValue(pendingSave);
    renderWithIntegrations(<Pyrat />, { PYRAT: state });
    await user.click(screen.getByRole("button", { name: "apps:integrations.pyrat.name" }));
    const apiKeyField = screen.getByLabelText("apps:integrations.pyrat.apiKeyLabel");

    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.clear" }));
    await user.click(screen.getByRole("button", { name: "common:actions.save" }));
    await waitFor(() =>
      expect(mockSaveAppOptions).toHaveBeenCalledWith(
        "PYRAT",
        Optional.present("1"),
        expect.objectContaining({ PYRAT_APIKEY: "" }),
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
});
