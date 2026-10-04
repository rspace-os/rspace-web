import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import type { SystemSettings } from "../../../../stores/stores/AuthStore";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import PIDINSTB2InstCard from "../PIDINSTB2InstCard";

describe("PIDINSTB2InstCard", () => {
  test("Should have no axe violations.", async () => {
    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <PIDINSTB2InstCard
          currentSettings={{
            enabled: "true",
            serverUrl: "https://b2inst.example.com",
            username: "",
            password: "",
            repositoryPrefix: "",
          }}
          isConflict={false}
          onEnabledChange={() => {}}
        />
      </ThemeProvider>,
    );

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(container).toBeAccessible();
  });

  test("Should have no axe violations when in conflict state.", async () => {
    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <PIDINSTB2InstCard
          currentSettings={{
            enabled: "true",
            serverUrl: "https://b2inst.example.com",
            username: "",
            password: "",
            repositoryPrefix: "",
          }}
          isConflict={true}
          onEnabledChange={() => {}}
        />
      </ThemeProvider>,
    );

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(container).toBeAccessible();
  });

  test("renders the secret as a password field that browsers will not autofill", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <PIDINSTB2InstCard
          currentSettings={{
            enabled: "true",
            serverUrl: "https://b2inst.example.com",
            username: "",
            password: "",
            repositoryPrefix: "",
          }}
          isConflict={false}
          onEnabledChange={() => {}}
        />
      </ThemeProvider>,
    );

    const secret = screen.getByLabelText("inventory:settings.pidinst.b2inst.labels.password");
    expect(secret).toHaveAttribute("type", "password");
    expect(secret).toHaveAttribute("autocomplete", "new-password");
  });

  const PASSWORD_LABEL = "inventory:settings.pidinst.b2inst.labels.password";
  const CLEAR = { name: "common:inputs.secretField.clear" };
  const SAVE = { name: "common:actions.save" };

  function renderCard(
    settings: Partial<SystemSettings["pidinstB2Inst"]>,
    updateSystemSettings = vi.fn().mockResolvedValue(undefined),
  ) {
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={makeMockRootStore({ authStore: { updateSystemSettings } })}>
          <PIDINSTB2InstCard
            currentSettings={{
              enabled: "true",
              serverUrl: "https://b2inst.example.com",
              username: "community",
              password: null,
              repositoryPrefix: "",
              ...settings,
            }}
            isConflict={false}
            onEnabledChange={() => {}}
          />
        </storesContext.Provider>
      </ThemeProvider>,
    );
    return updateSystemSettings;
  }

  test("returns a new secret to unchanged once it is saved", async () => {
    const user = userEvent.setup();
    const updateSystemSettings = renderCard({});
    const secret = screen.getByLabelText(PASSWORD_LABEL);

    await user.type(secret, "new-token");
    await user.click(screen.getByRole("button", SAVE));

    expect(updateSystemSettings).toHaveBeenCalledWith(
      "pidinstB2Inst",
      expect.objectContaining({ password: "new-token" }),
    );
    await waitFor(() => expect(secret).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged"));
  });

  test("cannot save an enabled integration with an empty field", async () => {
    const user = userEvent.setup();
    renderCard({});

    await user.clear(screen.getByRole("textbox", { name: "inventory:settings.pidinst.b2inst.labels.username" }));

    expect(screen.getByRole("button", SAVE)).toBeDisabled();
    expect(screen.queryByRole("button", CLEAR)).not.toBeInTheDocument();
  });

  test("clears the stored secret of a disabled integration", async () => {
    const user = userEvent.setup();
    const updateSystemSettings = renderCard({ enabled: "false" });

    await user.click(screen.getByRole("button", CLEAR));
    await user.click(screen.getByRole("button", SAVE));

    expect(updateSystemSettings).toHaveBeenCalledWith("pidinstB2Inst", expect.objectContaining({ password: "" }));
  });

  test("does not allow undo after clearing the stored secret has been saved", async () => {
    const user = userEvent.setup();
    const updateSystemSettings = renderCard({ enabled: "false" });

    await user.click(screen.getByRole("button", CLEAR));
    await user.click(screen.getByRole("button", SAVE));

    expect(updateSystemSettings).toHaveBeenCalledWith("pidinstB2Inst", expect.objectContaining({ password: "" }));
    expect(screen.queryByRole("button", { name: "common:inputs.secretField.undoClear" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "inventory:settings.pidinst.b2inst.enableLabel" }));
    expect(screen.getByRole("button", SAVE)).toBeDisabled();
  });

  test("disables editable settings while a save is pending", async () => {
    const user = userEvent.setup();
    let finishSave!: () => void;
    const savePromise = new Promise<void>((resolve) => {
      finishSave = resolve;
    });
    const updateSystemSettings = vi.fn(() => savePromise);
    renderCard({}, updateSystemSettings);

    const username = screen.getByRole("textbox", { name: "inventory:settings.pidinst.b2inst.labels.username" });
    await user.type(username, "-updated");
    await user.click(screen.getByRole("button", SAVE));
    await waitFor(() => expect(updateSystemSettings).toHaveBeenCalledOnce());

    expect(username).toBeDisabled();
    expect(screen.getByLabelText(PASSWORD_LABEL)).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: "inventory:settings.pidinst.b2inst.enableLabel" })).toBeDisabled();

    finishSave();
    await waitFor(() => expect(username).not.toBeDisabled());
  });
});
