import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, test } from "vitest";
import axios from "@/common/axios";
import type { IntegrationStates } from "@/eln/apps/useIntegrationsEndpoint";
import { Optional } from "@/util/optional";
import { renderWithIntegrations } from "../../__tests__/integrationsQueryClient";
import Fieldmark from "../Fieldmark";
import "@/__tests__/__mocks__/matchMedia";

const mockAxios = new MockAdapter(axios);

const state: IntegrationStates["FIELDMARK"] = {
  mode: "DISABLED",
  credentials: { FIELDMARK_USER_TOKEN: Optional.empty() },
};

async function typeAndSaveKey() {
  const user = userEvent.setup();
  renderWithIntegrations(<Fieldmark />, { FIELDMARK: state });
  await user.click(screen.getByRole("button", { name: "apps:integrations.fieldmark.name" }));
  const apiKeyField = screen.getAllByLabelText("apps:integrations.fieldmark.fields.apiKey")[0];
  await user.type(apiKeyField, "new-key");
  await user.click(screen.getByRole("button", { name: "common:actions.save" }));
  return apiKeyField;
}

describe("Fieldmark", () => {
  beforeEach(() => {
    mockAxios.reset();
  });

  test("keeps the typed API key when the save fails", async () => {
    mockAxios.onPost("integration/update").reply(200, { success: false, data: null, errorMsg: "save failed" });
    const apiKeyField = await typeAndSaveKey();

    await waitFor(() => {
      expect(mockAxios.history.post).toHaveLength(1);
    });
    await waitFor(() => expect(apiKeyField).not.toBeDisabled());
    expect(apiKeyField).toHaveValue("new-key");
  });

  test("locks the API key while a save is pending", async () => {
    const user = userEvent.setup();
    let finishSave!: () => void;
    mockAxios.onPost("integration/update").reply(
      () =>
        new Promise((resolve) => {
          finishSave = () =>
            resolve([
              200,
              {
                success: true,
                // the server returns a stored token as null
                data: { name: "FIELDMARK", available: true, enabled: false, options: { FIELDMARK_USER_TOKEN: null } },
              },
            ]);
        }),
    );
    renderWithIntegrations(<Fieldmark />, { FIELDMARK: state });
    await user.click(screen.getByRole("button", { name: "apps:integrations.fieldmark.name" }));
    const apiKeyField = screen.getAllByLabelText("apps:integrations.fieldmark.fields.apiKey")[0];

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
