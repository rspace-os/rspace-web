/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../__mocks__/matchMedia.js";
import React from "react";
import {
  cleanup,
  screen,
  waitFor,
  act,
  fireEvent,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import MockAdapter from "axios-mock-adapter";
import DMPDialog from "../DMPDialog";
import * as axios from "axios";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import { within, render } from "../../../__tests__/customQueries";
import { take, incrementForever } from "../../../util/iterators";

const mockAxios = new MockAdapter(axios);

beforeEach(() => {});

afterEach(cleanup);

describe("DMPDialog", () => {
  test("Should render mock data correctly.", async () => {
    jest.clearAllMocks();
    mockAxios.onGet(/\/apps\/argos\/plans.*/).reply(200, {
      data: {
        totalCount: 2,
        data: [
          {
            id: "e27789f1-de35-4b4a-9587-a46d131c366e",
            label: "Foo",
            grant: "Foo's grant",
            createdAt: 0,
            modifiedAt: 0,
          },
          {
            id: "e9a73d77-adfa-4546-974f-4a4a623b53a8",
            label: "Bar",
            grant: "Bar's grant",
            createdAt: 0,
            modifiedAt: 0,
          },
        ],
      },
      error: null,
      errorMsg: null,
      success: true,
    });
    mockAxios.resetHistory();
    render(
      <ThemeProvider theme={materialTheme}>
        <DMPDialog open={true} setOpen={() => {}} />
      </ThemeProvider>
    );

    await waitFor(
      () => {
        expect(screen.getAllByRole("row").length).toBeGreaterThan(1);
        // i.e. the table body has been rendered
      },
      { timeout: 2000 }
    );

    expect(
      await within(screen.getByRole("grid")).findTableCell({
        columnHeading: "Label",
        rowIndex: 0,
      })
    ).toHaveTextContent("Foo");
  });

  test(
    "Importing a selected DMP should call the import endpoint.",
    async () => {
      jest.clearAllMocks();
      mockAxios.onGet(/\/apps\/argos\/plans.*/).reply(200, {
        data: {
          totalCount: 2,
          data: [
            {
              id: "e27789f1-de35-4b4a-9587-a46d131c366e",
              label: "Foo",
              grant: "Foo's grant",
              createdAt: 0,
              modifiedAt: 0,
            },
            {
              id: "e9a73d77-adfa-4546-974f-4a4a623b53a8",
              label: "Bar",
              grant: "Bar's grant",
              createdAt: 0,
              modifiedAt: 0,
            },
          ],
        },
        error: null,
        errorMsg: null,
        success: true,
      });
      mockAxios.resetHistory();
      render(
        <ThemeProvider theme={materialTheme}>
          <DMPDialog open={true} setOpen={() => {}} />
        </ThemeProvider>
      );

      await waitFor(() => {
        expect(screen.getAllByRole("row").length).toBeGreaterThan(1);
        // i.e. the table body has been rendered
      });

      await act(async () => {
        (
          await within(
            await within(screen.getByRole("grid")).findTableCell({
              columnHeading: "Select",
              rowIndex: 0,
            })
          ).findByRole("radio")
        ).click();
      });

      mockAxios.resetHistory();

      await act(async () => {
        (await screen.findByRole("button", { name: "Import" })).click();
      });

      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].url).toBe(
        "/apps/argos/importPlan/e27789f1-de35-4b4a-9587-a46d131c366e"
      );
    },
    10 * 1000
  );

  describe("Pagination should work.", () => {
    test(
      "Next and previous page buttons should make the right API calls.",
      async () => {
        mockAxios.resetHistory();
        mockAxios.onGet(/\/apps\/argos\/plans.*/).reply(200, {
          data: {
            totalCount: 12,
            data: [...take(incrementForever(), 12)].map((n) => ({
              id: `${n}`,
              label: "Foo",
              grant: "Foo's grant",
              createdAt: 0,
              modifiedAt: 0,
            })),
          },
          error: null,
          errorMsg: null,
          success: true,
        });
        render(
          <ThemeProvider theme={materialTheme}>
            <DMPDialog open={true} setOpen={() => {}} />
          </ThemeProvider>
        );

        await waitFor(
          () => {
            expect(screen.getAllByRole("row").length).toBeGreaterThan(1);
            // i.e. the table body has been rendered
          },
          { timeout: 2000 }
        );

        await act(() => {
          screen
            .getByRole("button", {
              name: "Go to next page",
            })
            .click();
        });

        await act(() => {
          screen
            .getByRole("button", {
              name: "Go to previous page",
            })
            .click();
        });

        expect(mockAxios.history.get.length).toBe(3);
        expect(
          mockAxios.history.get.map(({ url }) =>
            new URLSearchParams(url.match(/\/apps\/argos\/plans?(.*)$/)[1]).get(
              "page"
            )
          )
        ).toEqual(["0", "1", "0"]);
      },
      20 * 1000
    );

    test(
      "Changing the page size should make the right API call.",
      async () => {
        jest.clearAllMocks();
        mockAxios.onGet(/\/apps\/argos\/plans.*/).reply(200, {
          data: {
            totalCount: 2,
            data: [
              {
                id: "e27789f1-de35-4b4a-9587-a46d131c366e",
                label: "Foo",
                grant: "Foo's grant",
                createdAt: 0,
                modifiedAt: 0,
              },
              {
                id: "e9a73d77-adfa-4546-974f-4a4a623b53a8",
                label: "Bar",
                grant: "Bar's grant",
                createdAt: 0,
                modifiedAt: 0,
              },
            ],
          },
          error: null,
          errorMsg: null,
          success: true,
        });
        mockAxios.resetHistory();
        mockAxios.onGet(/\/apps\/argos\/plans.*/).reply(200, {
          data: {
            totalCount: 12,
            data: [...take(incrementForever(), 12)].map((n) => ({
              id: `${n}`,
              label: "Foo",
              grant: "Foo's grant",
              createdAt: 0,
              modifiedAt: 0,
            })),
          },
          error: null,
          errorMsg: null,
          success: true,
        });
        render(
          <ThemeProvider theme={materialTheme}>
            <DMPDialog open={true} setOpen={() => {}} />
          </ThemeProvider>
        );

        await waitFor(
          () => {
            expect(screen.getAllByRole("row").length).toBeGreaterThan(1);
            // i.e. the table body has been rendered
          },
          { timeout: 2000 }
        );

        fireEvent.mouseDown(screen.getByRole("combobox"));

        await act(() => {
          within(screen.getByRole("listbox"))
            .getByRole("option", { name: 5 })
            .click();
        });

        expect(mockAxios.history.get.length).toBe(2);
        expect(
          mockAxios.history.get.map(({ url }) =>
            new URLSearchParams(url.match(/\/apps\/argos\/plans?(.*)$/)[1]).get(
              "pageSize"
            )
          )
        ).toEqual(["10", "5"]);
      },
      10 * 1000
    );
  });

  describe("Search filters should work.", () => {
    test(
      "Label filter should make the right API call.",
      async () => {
        jest.clearAllMocks();
        mockAxios.onGet(/\/apps\/argos\/plans.*/).reply(200, {
          data: {
            totalCount: 2,
            data: [
              {
                id: "e27789f1-de35-4b4a-9587-a46d131c366e",
                label: "Foo",
                grant: "Foo's grant",
                createdAt: 0,
                modifiedAt: 0,
              },
              {
                id: "e9a73d77-adfa-4546-974f-4a4a623b53a8",
                label: "Bar",
                grant: "Bar's grant",
                createdAt: 0,
                modifiedAt: 0,
              },
            ],
          },
          error: null,
          errorMsg: null,
          success: true,
        });
        mockAxios.resetHistory();
        render(
          <ThemeProvider theme={materialTheme}>
            <DMPDialog open={true} setOpen={() => {}} />
          </ThemeProvider>
        );

        await waitFor(
          () => {
            expect(screen.getAllByRole("row").length).toBeGreaterThan(1);
            // i.e. the table body has been rendered
          },
          { timeout: 2000 }
        );

        await act(() => {
          screen
            .getByRole("button", {
              name: "Label",
            })
            .click();
        });

        // first type in the label filter, and then press enter
        fireEvent.input(screen.getByRole("textbox"), {
          target: { value: "Foo" },
        });
        fireEvent.submit(screen.getByRole("textbox"), {
          target: { value: "" },
        });

        await waitFor(() => {
          expect(screen.getByText("Foo")).toBeVisible();
        });

        expect(mockAxios.history.get.length).toBe(2);
        expect(
          mockAxios.history.get.map(({ url }) =>
            new URLSearchParams(url.match(/\/apps\/argos\/plans?(.*)$/)[1]).get(
              "like"
            )
          )
        ).toEqual([null, "Foo"]);
      },
      10 * 1000
    );
  });
});
