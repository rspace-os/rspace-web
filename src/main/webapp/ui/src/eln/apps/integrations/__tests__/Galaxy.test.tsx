import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import type { IntegrationStates } from "@/eln/apps/useIntegrationsEndpoint";
import { Optional } from "@/util/optional";
import Galaxy from "../Galaxy";
import "@/__tests__/__mocks__/matchMedia";

const mockSaveAppOptions = vi.fn();
vi.mock("@/eln/apps/useIntegrationsEndpoint", () => ({
  useIntegrationsEndpoint: () => ({ saveAppOptions: mockSaveAppOptions, deleteAppOptions: vi.fn() }),
}));

const state: IntegrationStates["GALAXY"] = {
  mode: "DISABLED",
  credentials: {
    configuredServers: [],
    authenticatedServers: [{ alias: "main", url: "https://galaxy.example", apiKey: null, optionsId: "1" }],
  },
};

describe("Galaxy", () => {
  test("shows a new API key as unchanged once it is saved", async () => {
    const user = userEvent.setup();
    mockSaveAppOptions.mockResolvedValue(undefined);
    render(<Galaxy integrationState={state} update={() => {}} />);
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
});
