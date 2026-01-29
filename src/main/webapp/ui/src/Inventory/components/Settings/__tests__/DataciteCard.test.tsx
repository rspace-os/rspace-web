/*
 */
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import DataciteCard from "../DataciteCard";
import { axe } from "vitest-axe";
import { toHaveNoViolations } from "vitest-axe/matchers";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

expect.extend({ toHaveNoViolations });

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(cleanup);

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

    expect(await axe(container)).toHaveNoViolations();
  });
});


