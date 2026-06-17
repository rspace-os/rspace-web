import { test, describe, expect, beforeEach, vi } from 'vitest';
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/matchMedia";
import React from "react";
import { screen, waitFor, fireEvent, render } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import DMPDialog from "../DMPDialog";
import axios from "@/common/axios";
import { within, expectAccessible } from "@/__tests__/customQueries";
import { stubAppChrome } from "@/__tests__/helpers/appChrome";

import userEvent from "@testing-library/user-event";

const mockAxios = new MockAdapter(axios);

describe("DMPDialog", () => {
  beforeEach(() => {
    mockAxios.resetHistory();
    // importPlan calls a `gallery()` global defined on the legacy Gallery page;
    // stub it so the import success path completes cleanly under jsdom.
    (globalThis as unknown as { gallery: () => void }).gallery = () => {};
    stubAppChrome(mockAxios, {
      visibleTabs: { published: false, system: false },
    });
  })

  const plansResponse = {
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
  };

  test("Should render mock data correctly.", async () => {
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

  test("Should have no axe violations.", async () => {
    mockAxios.onGet(/\/apps\/argos\/plans.*/).reply(200, plansResponse);
    const { baseElement } = render(<DMPDialog open setOpen={() => {}} />);
    await waitFor(
      () => {
        expect(screen.getAllByRole("row").length).toBeGreaterThan(1);
        // i.e. the table body has been rendered
      },
      { timeout: 2000 }
    );
    await expectAccessible(baseElement);
  });

  test("Importing a selected DMP should call the import endpoint.", async () => {
    const user = userEvent.setup();
    mockAxios.onGet(/\/apps\/argos\/plans.*/).reply(200, plansResponse);
    mockAxios
      .onPost("/apps/argos/importPlan/e27789f1-de35-4b4a-9587-a46d131c366e")
      .reply(200);
    render(<DMPDialog open setOpen={() => {}} />);
    await waitFor(
      () => {
        expect(screen.getAllByRole("row").length).toBeGreaterThan(1);
        // i.e. the table body has been rendered
      },
      { timeout: 2000 }
    );
    await user.click(screen.getByRole("radio", { name: "Select plan: Foo" }));
    await user.click(screen.getByRole("button", { name: "Import" }));
    await waitFor(() => {
      expect(
        mockAxios.history.post.some(({ url }) =>
          /\/apps\/argos\/importPlan\/e27789f1-de35-4b4a-9587-a46d131c366e$/.test(
            url ?? ""
          )
        )
      ).toBe(true);
    });
  });

  describe.skip("Pagination should work.", () => {
    test(
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
    test(
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
    test(
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

