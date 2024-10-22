/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import MoveDialog from "../MoveDialog";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../../../accentedTheme";
import { COLOR } from "../../common";
import "../../../../../__mocks__/matchMedia";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const mockAxios = new MockAdapter(axios);

describe("MoveDialog", () => {
  test("Should request only folders", () => {
    render(
      <ThemeProvider theme={createAccentedTheme(COLOR)}>
        <MoveDialog
          open={true}
          onClose={() => {}}
          section="Images"
          refreshListing={() => {}}
        />
      </ThemeProvider>
    );

    expect(mockAxios.history.get.length).toBe(1);
    expect(mockAxios.history.get[0].params.get("foldersOnly")).toBe("true");
  });
});
