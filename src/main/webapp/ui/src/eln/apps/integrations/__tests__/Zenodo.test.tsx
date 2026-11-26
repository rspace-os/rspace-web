import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, test } from "vitest";
import axios from "@/common/axios";
import { Optional } from "../../../../util/optional";
import Zenodo from "../Zenodo";
import "@/__tests__/__mocks__/matchMedia";
import { renderWithIntegrations } from "../../__tests__/integrationsQueryClient";
import type { FetchedState, IntegrationStates } from "../../useIntegrationsEndpoint";

const mockAxios = new MockAdapter(axios);

/* The server replies with the saved state, returning a stored token as null. */
function savedState(posted: FetchedState): FetchedState {
  const token = posted.options.ZENODO_USER_TOKEN;
  return { ...posted, options: { ZENODO_USER_TOKEN: token === "" ? "" : null } };
}

function postedUpdates(): Array<FetchedState> {
  return mockAxios.history.post.map(({ data }) => JSON.parse(data as string) as FetchedState);
}

const disabledWithoutKey: IntegrationStates["ZENODO"] = {
  mode: "DISABLED",
  credentials: { ZENODO_USER_TOKEN: Optional.present("") },
};

function renderZenodo(state: IntegrationStates["ZENODO"] = disabledWithoutKey) {
  return renderWithIntegrations(<Zenodo />, { ZENODO: state });
}

describe("Zenodo", () => {
  beforeEach(() => {
    mockAxios.reset();
    mockAxios.onPost("integration/update").reply((config) => {
      const posted = JSON.parse(config.data as string) as FetchedState;
      return [200, { success: true, data: savedState(posted) }];
    });
  });
  test("should open dialog when Zenodo button is clicked", async () => {
    const user = userEvent.setup();

    renderZenodo();
    await waitFor(() => {
      screen.getByText("apps:integrations.zenodo.name");
    });
    await user.click(screen.getByRole("button", { name: "apps:integrations.zenodo.name" }));
    expect(screen.getByRole("dialog")).toBeVisible();
  });
  test("should close dialog when close button is clicked", async () => {
    const user = userEvent.setup();

    renderZenodo();
    await waitFor(() => {
      screen.getByText("apps:integrations.zenodo.name");
    });
    // Open dialog
    await user.click(screen.getByRole("button", { name: "apps:integrations.zenodo.name" }));

    expect(screen.getByRole("dialog")).toBeVisible();
    // Close dialog
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "common:actions.close" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });
  test("should enable integration when dialog is open and integration is disabled", async () => {
    const user = userEvent.setup();

    renderZenodo();
    await waitFor(() => {
      screen.getByText("apps:integrations.zenodo.name");
    });
    // Open dialog
    await user.click(screen.getByRole("button", { name: "apps:integrations.zenodo.name" }));

    expect(screen.getByRole("dialog")).toBeVisible();
    // Enable integration
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "apps:integrationCard.enable" }));
    await waitFor(() => {
      expect(
        within(screen.getByRole("dialog")).getByRole("button", { name: "apps:integrationCard.disable" }),
      ).toBeVisible();
    });
    // Verify update was called with correct arguments
    expect(postedUpdates()).toEqual([
      { name: "ZENODO", available: true, enabled: true, options: { ZENODO_USER_TOKEN: "" } },
    ]);
  });
  test("should disable integration when dialog is open and integration is enabled", async () => {
    const user = userEvent.setup();
    renderZenodo({
      mode: "ENABLED",
      credentials: { ZENODO_USER_TOKEN: Optional.present("test-token") },
    });
    await waitFor(() => {
      screen.getByText("apps:integrations.zenodo.name");
    });
    // Open dialog
    await user.click(screen.getByRole("button", { name: "apps:integrations.zenodo.name" }));

    expect(screen.getByRole("dialog")).toBeVisible();
    // Disable integration
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "apps:integrationCard.disable" }));
    await waitFor(() => {
      expect(
        within(screen.getByRole("dialog")).getByRole("button", { name: "apps:integrationCard.enable" }),
      ).toBeVisible();
    });
    // Verify update was called with correct arguments
    expect(postedUpdates()).toEqual([
      { name: "ZENODO", available: true, enabled: false, options: { ZENODO_USER_TOKEN: "test-token" } },
    ]);
  });
  test("should set API key when dialog is open", async () => {
    const user = userEvent.setup();

    const apiKey = "test-api-key-123";

    renderZenodo();
    await waitFor(() => {
      screen.getByText("apps:integrations.zenodo.name");
    });
    // Open dialog
    await user.click(screen.getByRole("button", { name: "apps:integrations.zenodo.name" }));

    expect(screen.getByRole("dialog")).toBeVisible();
    // Set API key
    /*
     * We have to use getByLabelText instead of getByRole because password
     * fields do not have a role. For more info, see
     * https://github.com/testing-library/dom-testing-library/issues/567
     */
    await user.type(screen.getAllByLabelText("apps:integrations.zenodo.fields.apiKey")[0], apiKey);

    await user.click(screen.getByRole("button", { name: "common:actions.save" }));
    // Verify update was called with correct arguments
    await waitFor(() => {
      expect(postedUpdates()).toEqual([
        { name: "ZENODO", available: true, enabled: false, options: { ZENODO_USER_TOKEN: apiKey } },
      ]);
    });
  });
  test("should open, set API key, enable, and close dialog in sequence", async () => {
    const user = userEvent.setup();

    const apiKey = "my-zenodo-api-key";

    renderZenodo();
    await waitFor(() => {
      screen.getByText("apps:integrations.zenodo.name");
    });
    // Open dialog
    await user.click(screen.getByRole("button", { name: "apps:integrations.zenodo.name" }));

    expect(screen.getByRole("dialog")).toBeVisible();
    // Set API key
    const apiKeyField = screen.getAllByLabelText("apps:integrations.zenodo.fields.apiKey")[0];
    await user.type(apiKeyField, apiKey);

    await user.click(screen.getByRole("button", { name: "common:actions.save" }));
    await waitFor(() => {
      expect(apiKeyField).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
    });
    // Enable integration
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "apps:integrationCard.enable" }));
    await waitFor(() => {
      expect(
        within(screen.getByRole("dialog")).getByRole("button", { name: "apps:integrationCard.disable" }),
      ).toBeVisible();
    });
    // the key is set first, then enabling keeps the stored key by posting null
    expect(postedUpdates()).toEqual([
      { name: "ZENODO", available: true, enabled: false, options: { ZENODO_USER_TOKEN: apiKey } },
      { name: "ZENODO", available: true, enabled: true, options: { ZENODO_USER_TOKEN: null } },
    ]);
    // Close dialog
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "common:actions.close" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });
  test("locks the API key while a save is pending", async () => {
    const user = userEvent.setup();
    let finishSave!: () => void;
    mockAxios.onPost("integration/update").reply(
      (config) =>
        new Promise((resolve) => {
          const posted = JSON.parse(config.data as string) as FetchedState;
          finishSave = () => resolve([200, { success: true, data: savedState(posted) }]);
        }),
    );
    renderZenodo();
    await user.click(screen.getByRole("button", { name: "apps:integrations.zenodo.name" }));
    const apiKeyField = screen.getAllByLabelText("apps:integrations.zenodo.fields.apiKey")[0];

    await user.type(apiKeyField, "new-key");
    await user.click(screen.getByRole("button", { name: "common:actions.save" }));
    await waitFor(() => expect(mockAxios.history.post).toHaveLength(1));

    expect(apiKeyField).toBeDisabled();
    expect(screen.getByRole("button", { name: "common:actions.save" })).toBeDisabled();
    await user.type(apiKeyField, "replacement");
    expect(apiKeyField).toHaveValue("new-key");

    finishSave();
    await waitFor(() => expect(apiKeyField).not.toBeDisabled());
    expect(apiKeyField).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
    expect(apiKeyField).toHaveValue("");
  });
});
