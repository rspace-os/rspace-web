import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import type { IntegrationStates } from "@/eln/apps/useIntegrationsEndpoint";
import { Optional } from "@/util/optional";
import Fieldmark from "../Fieldmark";
import "@/__tests__/__mocks__/matchMedia";

const state: IntegrationStates["FIELDMARK"] = {
  mode: "DISABLED",
  credentials: { FIELDMARK_USER_TOKEN: Optional.empty() },
};

async function typeAndSaveKey(update: (s: IntegrationStates["FIELDMARK"]) => Promise<void>) {
  const user = userEvent.setup();
  render(<Fieldmark integrationState={state} update={update} />);
  await user.click(screen.getByRole("button", { name: "apps:integrations.fieldmark.name" }));
  const apiKeyField = screen.getAllByLabelText("apps:integrations.fieldmark.fields.apiKey")[0];
  await user.type(apiKeyField, "new-key");
  await user.click(screen.getByRole("button", { name: "common:actions.save" }));
  return apiKeyField;
}

describe("Fieldmark", () => {
  test("shows the API key as unchanged once the save succeeds", async () => {
    const apiKeyField = await typeAndSaveKey(vi.fn().mockResolvedValue(undefined));

    await waitFor(() => {
      expect(apiKeyField).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
    });
  });

  test("keeps the typed API key when the save fails", async () => {
    const update = vi.fn().mockRejectedValue(new Error("save failed"));
    const apiKeyField = await typeAndSaveKey(update);

    await waitFor(() => {
      expect(update).toHaveBeenCalled();
    });
    expect(apiKeyField).toHaveValue("new-key");
  });

  test("locks the API key while a save is pending", async () => {
    const user = userEvent.setup();
    let finishSave!: () => void;
    const pendingSave = new Promise<void>((resolve) => {
      finishSave = resolve;
    });
    const update = vi.fn(() => pendingSave);
    render(<Fieldmark integrationState={state} update={update} />);
    await user.click(screen.getByRole("button", { name: "apps:integrations.fieldmark.name" }));
    const apiKeyField = screen.getAllByLabelText("apps:integrations.fieldmark.fields.apiKey")[0];

    await user.type(apiKeyField, "new-key");
    await user.click(screen.getByRole("button", { name: "common:actions.save" }));
    await waitFor(() => expect(update).toHaveBeenCalledOnce());

    expect(apiKeyField).toBeDisabled();
    expect(screen.getByRole("button", { name: "common:actions.save" })).toBeDisabled();
    await user.type(apiKeyField, "replacement");
    expect(apiKeyField).toHaveValue("new-key");

    finishSave();
    await waitFor(() => expect(apiKeyField).not.toBeDisabled());
    expect(apiKeyField).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
  });
});
