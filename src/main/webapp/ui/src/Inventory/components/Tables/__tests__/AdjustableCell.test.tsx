/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import {
  type AdjustableTableRow,
  type CellContent,
} from "../../../../stores/definitions/Tables";
import AdjustableCell from "../AdjustableCell";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import RecordLocation from "../../../../Inventory/components/RecordLocation";

jest.mock("../../RecordLocation", () => jest.fn(() => <span></span>));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("AdjustableCell", () => {
  describe("Location", () => {
    test("render a TopLink component when passed a root level container.", () => {
      const adjustableTableRow: AdjustableTableRow<"foo"> = {
        adjustableTableOptions() {
          return new Map<"foo", () => CellContent>([
            [
              "foo",
              () => ({
                renderOption: "location",
                data: makeMockContainer(),
              }),
            ],
          ]);
        },
      };

      render(
        <table>
          <tbody>
            <tr>
              <AdjustableCell
                dataSource={adjustableTableRow}
                selectedOption="foo"
              />
            </tr>
          </tbody>
        </table>
      );

      expect(RecordLocation).toHaveBeenCalled();
    });
  });
});
