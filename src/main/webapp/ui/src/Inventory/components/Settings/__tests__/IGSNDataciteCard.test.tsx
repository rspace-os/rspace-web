import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
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
});
