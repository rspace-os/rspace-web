/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom";
import RecordLocation from "../../../../Inventory/components/RecordLocation";
import type { AdjustableTableRow, CellContent } from "../../../../stores/definitions/Tables";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import AdjustableCell from "../AdjustableCell";

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
                            <AdjustableCell dataSource={adjustableTableRow} selectedOption="foo" />
                        </tr>
                    </tbody>
                </table>,
            );

            expect(RecordLocation).toHaveBeenCalled();
        });
    });
});
