import { ThemeProvider } from "@mui/material/styles";
import { render } from "@testing-library/react";
import { describe, expect, test } from "vitest";
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
});
