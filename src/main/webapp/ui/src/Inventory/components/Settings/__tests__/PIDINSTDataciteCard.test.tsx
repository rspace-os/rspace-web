import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import PIDINSTDataciteCard from "../PIDINSTDataciteCard";

describe("PIDINSTDataciteCard", () => {
  test("Should have no axe violations.", async () => {
    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <PIDINSTDataciteCard
          currentSettings={{
            enabled: "true",
            serverUrl: "https://api.datacite.org",
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
        <PIDINSTDataciteCard
          currentSettings={{
            enabled: "true",
            serverUrl: "https://api.datacite.org",
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
          <PIDINSTDataciteCard
            currentSettings={{
              enabled: "true",
              serverUrl: "https://api.datacite.org",
              username: "community",
              password: null,
              repositoryPrefix: "prefix",
            }}
            isConflict={false}
            onEnabledChange={() => {}}
          />
        </storesContext.Provider>
      </ThemeProvider>,
    );

    const username = screen.getByRole("textbox", { name: "inventory:settings.pidinst.datacite.labels.username" });
    await user.type(username, "-updated");
    await user.click(screen.getByRole("button", { name: "common:actions.save" }));
    await waitFor(() => expect(updateSystemSettings).toHaveBeenCalledOnce());

    expect(username).toBeDisabled();
    expect(screen.getByLabelText("inventory:settings.pidinst.datacite.labels.password")).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: "inventory:settings.pidinst.datacite.enableLabel" })).toBeDisabled();
    for (const radio of screen.getAllByRole("radio")) expect(radio).toBeDisabled();

    finishSave();
    await waitFor(() => expect(username).not.toBeDisabled());
  });
});
