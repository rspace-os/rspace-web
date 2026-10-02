import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
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

  test("renders the secret as a password field that browsers will not autofill", () => {
    render(
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

    const secret = screen.getByLabelText("inventory:settings.pidinst.datacite.labels.password");
    expect(secret).toHaveAttribute("type", "password");
    expect(secret).toHaveAttribute("autocomplete", "new-password");
  });
});
