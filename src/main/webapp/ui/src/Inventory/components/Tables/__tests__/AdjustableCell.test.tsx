import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import RecordLocation from "../../../../Inventory/components/RecordLocation";
import NavigateContext from "../../../../stores/contexts/Navigate";
import type { AdjustableTableRow, CellContent } from "../../../../stores/definitions/Tables";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import { Optional } from "../../../../util/optional";
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
  describe("Tags", () => {
    test.each(["Salt & pepper", "Batch #2"])("clicking %s searches for the whole tag", async (value) => {
      const user = userEvent.setup();
      const navigate = vi.fn();
      const adjustableTableRow: AdjustableTableRow<"foo"> = {
        adjustableTableOptions() {
          return new Map<"foo", () => CellContent>([
            [
              "foo",
              () => ({
                renderOption: "tags",
                data: [
                  {
                    value,
                    uri: Optional.empty<string>(),
                    vocabulary: Optional.empty<string>(),
                    version: Optional.empty<string>(),
                  },
                ],
              }),
            ],
          ]);
        },
      };
      render(
        <NavigateContext.Provider value={{ useNavigate: () => navigate, useLocation: vi.fn() }}>
          <table>
            <tbody>
              <tr>
                <AdjustableCell dataSource={adjustableTableRow} selectedOption="foo" />
              </tr>
            </tbody>
          </table>
        </NavigateContext.Provider>,
      );

      await user.click(screen.getByText(value));

      const url = new URL(navigate.mock.calls[0][0] as string, "http://localhost");
      expect(url.searchParams.get("query")).toBe(`l: (tags:"${value}")`);
    });
  });
});
