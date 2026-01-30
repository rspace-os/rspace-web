/*
 */
import {
  describe,
  test,
  expect,
  vi,
  beforeEach,
} from "vitest";
import "../../../../__mocks__/matchMedia";
import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import DMPDialog from "../DMPDialog";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  vi.clearAllMocks();
});


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

    let resolvePublic: (() => void) | null = null;
    const publicResponse = new Promise<void>((resolve) => {
      resolvePublic = resolve;
    });
    mockAxios.onGet("/apps/dmptool/plans?scope=PUBLIC").reply(() =>
      publicResponse.then(() => [
        200,
        {
          data: {
            items: [
              { dmp: { id: 1, title: "public", description: "very public" } },
            ],
          },
          success: true,
        },
      ]),
    );

    render(
      <ThemeProvider theme={materialTheme}>
        <DMPDialog open setOpen={() => {}} />
      </ThemeProvider>
    );

    // public will take a second to return a listing
    fireEvent.click(screen.getByRole("radio", { name: "Public" }));

    // but mine will return immediately
    fireEvent.click(screen.getByRole("radio", { name: "Mine" }));

    await waitFor(() => {
      expect(screen.getByText("mine")).toBeVisible();
    });

    await act(async () => {
      resolvePublic?.();
    });

    await waitFor(() => {
      expect(screen.queryByText("public")).not.toBeInTheDocument();
    });
  });
});
