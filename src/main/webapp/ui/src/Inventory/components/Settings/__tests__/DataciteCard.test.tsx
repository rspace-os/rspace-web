/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import DataciteCard from "../DataciteCard";
import { axe, toHaveNoViolations } from "jest-axe";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

expect.extend(toHaveNoViolations);

beforeEach(() => {
  jest.clearAllMocks();
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
