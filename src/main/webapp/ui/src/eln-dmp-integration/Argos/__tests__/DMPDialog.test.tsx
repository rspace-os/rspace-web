/*
 */
import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import "../../../../__mocks__/matchMedia";
import React from "react";
import {
  screen,
  waitFor,
  fireEvent,
} from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import DMPDialog from "../DMPDialog";
import axios from "@/common/axios";
import { render, within } from "../../../__tests__/customQueries";
import userEvent from "@testing-library/user-event";

const mockAxios = new MockAdapter(axios);

// This test suite is skipped as JSOM is generating nonsensical selectors (e.g. button,,,,Ark,,,A.MuiButtonBase-root .MuiInputAdornment-positionStart)
// TODO: Revisit this test when we switch to Vitest or upgrade MUI
describe.skip("DMPDialog", () => {
  beforeEach(() => {
    mockAxios.resetHistory();

    mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, {
      data: "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3MzQzNDI5NTYsImV4cCI6MTczNDM0NjU1NiwicmVmcmVzaFRva2VuSGFzaCI6ImZlMTVmYTNkNWUzZDVhNDdlMzNlOWUzNDIyOWIxZWEyMzE0YWQ2ZTZmMTNmYTQyYWRkY2E0ZjE0Mzk1ODJhNGQifQ.HCKre3g_P1wmGrrrnQncvFeT9pAePFSc4UPuyP5oehI",
    });

    mockAxios.onGet("/api/v1/userDetails/uiNavigationData").reply(
      200,
      {
        bannerImgSrc: "/public/banner",
        visibleTabs: {
          inventory: true,
          myLabGroups: true,
          published: false,
          system: false,
        },
        userDetails: {
          username: "user1a",
          fullName: "user user",
          email: "user@user.com",
          orcidId: null,
          orcidAvailable: false,
          profileImgSrc: null,
          lastSession: "2025-03-25T15:45:57.000Z",
        },
        operatedAs: false,
        nextMaintenance: null,
      },
      {
        contentType: "application/json",
      }
    );
  })
  it("Should render mock data correctly.", async () => {
    vi.clearAllMocks();
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
      <DMPDialog open={true} setOpen={() => {}} />
    );

    await waitFor(
      () => {
        expect(screen.getAllByRole("row").length).toBeGreaterThan(1);
        // i.e. the table body has been rendered
      },
      { timeout: 2000 }
    );

    expect(
      await(
        within as (element: HTMLElement) => {
          findTableCell: (options: {
            columnHeading: string;
            rowIndex: number;
          }) => Promise<HTMLElement>;
        }
      )(screen.getByRole("grid")).findTableCell({
        columnHeading: "Label",
        rowIndex: 0,
      })
    ).toHaveTextContent("Foo");
  });

  describe.skip("Pagination should work.", () => {
    it(
      "Next and previous page buttons should make the right API calls.",
      async () => {
        const user = userEvent.setup();
        mockAxios.onGet(/\/apps\/argos\/plans.*/).reply(200, {
          data: {
            totalCount: 12,
            data: [...Array(12).keys()].map((n) => ({
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
            <DMPDialog open={true} setOpen={() => {}} />
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
          /\/apps\/argos\/plans/.test(url ?? "")
        );
        expect(plansRequests.length).toBe(3);
        expect(
          plansRequests.map(({ url }) =>
            new URLSearchParams(
              url?.match(/\/apps\/argos\/plans?(.*)$/)?.[1]
            ).get("page")
          )
        ).toEqual(["0", "1", "0"]);
      },
      20 * 1000
    );

    it(
      "Changing the page size should make the right API call.",
      async () => {
        const user = userEvent.setup();
        vi.clearAllMocks();
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
            data: [...Array(12).keys()].map((n) => ({
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
          <DMPDialog open={true} setOpen={() => {}} />
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
          /\/apps\/argos\/plans/.test(url ?? "")
        );
        expect(plansRequests.length).toBe(2);
        expect(
          plansRequests.map(({ url }) =>
            new URLSearchParams(
              url?.match(/\/apps\/argos\/plans?(.*)$/)?.[1]
            ).get("pageSize")
          )
        ).toEqual(["10", "5"]);
      },
      10 * 1000
    );
  });

  describe.skip("Search filters should work.", () => {
    it(
      "Label filter should make the right API call.",
      async () => {
        const user = userEvent.setup();
        vi.clearAllMocks();
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
          <DMPDialog open={true} setOpen={() => {}} />
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
          /\/apps\/argos\/plans/.test(url ?? "")
        );
        expect(plansRequests.length).toBe(2);
        expect(
          plansRequests.map(({ url }) =>
            new URLSearchParams(
              url?.match(/\/apps\/argos\/plans?(.*)$/)?.[1]
            ).get("like")
          )
        ).toEqual([null, "Foo"]);
      },
      10 * 1000
    );
  });
});


