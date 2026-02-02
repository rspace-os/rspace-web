import { test, describe, expect, vi } from 'vitest';
import React from "react";
import {
  render,
} from "@testing-library/react";
import DataciteCard from "../DataciteCard";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
describe("DataciteCard", () => {
  test("Should have no axe violations.", async () => {
    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <DataciteCard
          currentSettings={{
            enabled: "true",
            serverUrl: "https://api.datacite.org",
            username: "",
            password: "",
            repositoryPrefix: "",
          }}
        />
      </ThemeProvider>
    );
    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(container).toBeAccessible();
  });
});
