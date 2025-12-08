/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import fc from "fast-check";
import { makeMockSubSample, subSampleAttrsArbitrary } from "../../../../stores/models/__tests__/SubSampleModel/mocking";
import materialTheme from "../../../../theme";
import SelectAction from "../SelectAction";

jest.mock("../../../../common/InvApiService", () => {});

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("SelectAction", () => {
    describe("Shows a badge that should", () => {
        test("display the count of selected records.", () => {
            fc.assert(
                fc.property(
                    /*
                     * Only needs to be supported up to 100 as a user cannot select more
                     * than 100 items in list view, which is the only place that the
                     * SelectAction is shown. Elements of array can of any type because
                     * SelectAction doesn't actually care.
                     */
                    fc.array(subSampleAttrsArbitrary.map(makeMockSubSample), {
                        minLength: 1,
                        maxLength: 100,
                    }),
                    (selectedItems) => {
                        const { container } = render(
                            <ThemeProvider theme={materialTheme}>
                                <SelectAction
                                    as="button"
                                    disabled=""
                                    selectedResults={selectedItems}
                                    onSelectOptions={[]}
                                />
                            </ThemeProvider>,
                        );
                        expect(container).toHaveTextContent(String(selectedItems.length));
                    },
                ),
            );
        });
    });
});
