/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../__mocks__/matchMedia.js";
import React from "react";
import {
  render,
  cleanup,
  screen,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import DMPDialog from "../DMPDialog";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import { sleep } from "../../../util/Util";

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("DMPDialog", () => {
  test("Label is shown when no DMPs are returned.", async () => {
    mockAxios
      .onGet("/apps/dmptool/plans?scope=MINE")
      .reply(200, { data: { items: [] }, success: true });

    render(
      <ThemeProvider theme={materialTheme}>
        <DMPDialog open setOpen={() => {}} />
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("No DMPs")).toBeVisible();
    });
  });

  test("The latest request is always the one that's shown.", async () => {
    mockAxios.onGet("/apps/dmptool/plans?scope=MINE").reply(200, {
      data: {
        items: [{ dmp: { id: 1, title: "mine", description: "very mine" } }],
      },
      success: true,
    });

    mockAxios.onGet("/apps/dmptool/plans?scope=PUBLIC").reply(async () => {
      // server takes longer to process a much longer list
      await sleep(1000);
      return [
        200,
        {
          data: {
            items: [
              { dmp: { id: 1, title: "public", description: "very public" } },
            ],
          },
          success: true,
        },
      ];
    });

    render(
      <ThemeProvider theme={materialTheme}>
        <DMPDialog open setOpen={() => {}} />
      </ThemeProvider>
    );

    // public will take a second to return a listing
    fireEvent.click(screen.getByRole("radio", { name: "Public" }));

    // but mine will return immediately
    fireEvent.click(screen.getByRole("radio", { name: "Mine" }));

    /*
     * in these two seconds, the mine request will return, and then a second
     * later the public request will return. The public one should be ignored
     * because the user tapped mine after.
     */
    await sleep(2000);

    await waitFor(() => {
      expect(screen.getByText("mine")).toBeVisible();
    });
  });
});
