/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../__mocks__/matchMedia.js";
import React from "react";
import { cleanup, screen, waitFor, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import MockAdapter from "axios-mock-adapter";
import DMPDialog from "../DMPDialog";
import * as axios from "axios";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import { within, render } from "../../../__tests__/customQueries";
import { take, incrementForever } from "../../../util/iterators";
import userEvent from "@testing-library/user-event";
import { axe, toHaveNoViolations } from "jest-axe";

expect.extend(toHaveNoViolations);

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
      const user = userEvent.setup();
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

      await user.click(
        await within(
          await within(screen.getByRole("grid")).findTableCell({
            columnHeading: "Select",
            rowIndex: 0,
          })
        ).findByRole("radio")
      );

      mockAxios.resetHistory();

      await user.click(await screen.findByRole("button", { name: "Import" }));

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
        const user = userEvent.setup();
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

        await user.click(
          screen.getByRole("button", {
            name: "Go to next page",
          })
        );

        await user.click(
          screen.getByRole("button", {
            name: "Go to previous page",
          })
        );

        const plansRequests = mockAxios.history.get.filter(({ url }) =>
          /\/apps\/argos\/plans/.test(url)
        );
        expect(plansRequests.length).toBe(3);
        expect(
          plansRequests.map(({ url }) =>
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
        const user = userEvent.setup();
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

        await user.click(
          within(screen.getByRole("listbox")).getByRole("option", { name: "5" })
        );

        const plansRequests = mockAxios.history.get.filter(({ url }) =>
          /\/apps\/argos\/plans/.test(url)
        );
        expect(plansRequests.length).toBe(2);
        expect(
          plansRequests.map(({ url }) =>
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
        const user = userEvent.setup();
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

        await user.click(
          screen.getByRole("button", {
            name: "Label",
          })
        );

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

        const plansRequests = mockAxios.history.get.filter(({ url }) =>
          /\/apps\/argos\/plans/.test(url)
        );
        expect(plansRequests.length).toBe(2);
        expect(
          plansRequests.map(({ url }) =>
            new URLSearchParams(url.match(/\/apps\/argos\/plans?(.*)$/)[1]).get(
              "like"
            )
          )
        ).toEqual([null, "Foo"]);
      },
      10 * 1000
    );
  });
  test("Should have no axe violations.", async () => {
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
    const { baseElement } = render(
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

    // $FlowExpectedError[incompatible-call] See expect.extend above
    expect(await axe(baseElement)).toHaveNoViolations();
  });
});
