import { test, describe, expect, vi } from 'vitest';
import React from "react";
import {
  render,
} from "@testing-library/react";
import {
  type AdjustableTableRow,
  type CellContent,
} from "../../../../stores/definitions/Tables";
import AdjustableCell from "../AdjustableCell";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import RecordLocation from "../../../../Inventory/components/RecordLocation";

vi.mock("../../RecordLocation", () => ({
  default: vi.fn(() => <span></span>),
}));




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

