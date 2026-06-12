import { render } from "@testing-library/react";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test, vi } from "vitest";
import RecordLocation from "../../../../Inventory/components/RecordLocation";
// biome-ignore lint/style/useImportType: initial biome migration
import { type AdjustableTableRow, type CellContent } from "../../../../stores/definitions/Tables";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import AdjustableCell from "../AdjustableCell";

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
              <AdjustableCell dataSource={adjustableTableRow} selectedOption="foo" />
            </tr>
          </tbody>
        </table>,
      );
      expect(RecordLocation).toHaveBeenCalled();
    });
  });
});
