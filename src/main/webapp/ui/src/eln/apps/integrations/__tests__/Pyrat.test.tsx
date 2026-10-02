import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import type { IntegrationStates } from "@/eln/apps/useIntegrationsEndpoint";
import { Optional } from "@/util/optional";
import Pyrat from "../Pyrat";
import "@/__tests__/__mocks__/matchMedia";

const mockSaveAppOptions = vi.fn();
vi.mock("@/eln/apps/useIntegrationsEndpoint", () => ({
  useIntegrationsEndpoint: () => ({ saveAppOptions: mockSaveAppOptions, deleteAppOptions: vi.fn() }),
}));

const state: IntegrationStates["PYRAT"] = {
  mode: "DISABLED",
  credentials: {
    configuredServers: [],
    authenticatedServers: [{ alias: "main", url: "https://pyrat.example", apiKey: null, optionsId: "1" }],
  },
};

describe("Pyrat", () => {
  test("shows a new API key as unchanged once it is saved", async () => {
    const user = userEvent.setup();
    mockSaveAppOptions.mockResolvedValue(undefined);
    render(<Pyrat integrationState={state} update={() => {}} />);
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
});
