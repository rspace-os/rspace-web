import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import IGSNDataciteCard from "../IGSNDataciteCard";

describe("IGSNDataciteCard", () => {
  test("Should have no axe violations.", async () => {
    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <IGSNDataciteCard
          currentSettings={{
            enabled: "true",
            serverUrl: "https://api.datacite.org",
            username: "",
            password: "",
            repositoryPrefix: "",
          }}
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
        <IGSNDataciteCard
          currentSettings={{
            enabled: "true",
            serverUrl: "https://api.datacite.org",
            username: "",
            password: "",
            repositoryPrefix: "",
          }}
          onEnabledChange={() => {}}
        />
      </ThemeProvider>,
    );

    const secret = screen.getByLabelText("inventory:settings.datacite.labels.password");
    expect(secret).toHaveAttribute("type", "password");
    expect(secret).toHaveAttribute("autocomplete", "new-password");
  });

  test("disables editable settings while a save is pending", async () => {
    const user = userEvent.setup();
    let finishSave!: () => void;
    const savePromise = new Promise<void>((resolve) => {
      finishSave = resolve;
    });
    const updateSystemSettings = vi.fn(() => savePromise);

    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={makeMockRootStore({ authStore: { updateSystemSettings } })}>
          <IGSNDataciteCard
            currentSettings={{
              enabled: "true",
              serverUrl: "https://api.datacite.org",
              username: "community",
              password: null,
              repositoryPrefix: "prefix",
            }}
            onEnabledChange={() => {}}
          />
        </storesContext.Provider>
      </ThemeProvider>,
    );

    const username = screen.getByRole("textbox", { name: "inventory:settings.datacite.labels.username" });
    await user.type(username, "-updated");
    await user.click(screen.getByRole("button", { name: "common:actions.save" }));
    await waitFor(() => expect(updateSystemSettings).toHaveBeenCalledOnce());

    expect(username).toBeDisabled();
    expect(screen.getByLabelText("inventory:settings.datacite.labels.password")).toBeDisabled();
    for (const radio of screen.getAllByRole("radio")) expect(radio).toBeDisabled();

    finishSave();
    await waitFor(() => expect(username).not.toBeDisabled());
  });
});
