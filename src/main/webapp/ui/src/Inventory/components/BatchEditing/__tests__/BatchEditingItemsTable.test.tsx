/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet } from "../../../../util/__tests__/set/helpers";
import { arbitraryRecord } from "../../../../stores/definitions/__tests__/Record/helper";
import BatchEditingItemsTable from "../BatchEditingItemsTable";
import "../../../../../__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("BatchEditingItemsTable", () => {
  test("Table body should have as many rows as records that are passed.", () => {
    void fc.assert(
      fc.asyncProperty(
        fc.tuple(arbRsSet(arbitraryRecord), fc.string()),
        async ([records, label]) => {
          fc.pre(records.map(({ globalId }) => globalId).size === records.size);
          const user = userEvent.setup();
          cleanup();
          render(
            <ThemeProvider theme={materialTheme}>
              <BatchEditingItemsTable records={records} label={label} />
            </ThemeProvider>
          );

          await user.click(screen.getByRole("button"));
          // + 1 for the table head
          expect(screen.queryAllByRole("row").length).toEqual(records.size + 1);
        }
      ),
      { numRuns: 10 }
    );
  });
});
