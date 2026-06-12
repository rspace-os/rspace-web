import { render } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import RecordLocation from "../../../../Inventory/components/RecordLocation";
import type { AdjustableTableRow, CellContent } from "../../../../stores/definitions/Tables";
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
