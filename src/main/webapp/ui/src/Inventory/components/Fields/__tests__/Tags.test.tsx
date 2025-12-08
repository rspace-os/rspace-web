/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import fc from "fast-check";
import materialTheme from "../../../../theme";
import { Optional } from "../../../../util/optional";
import Tags from "../Tags";
import "../../../../../__mocks__/matchMedia";

window.fetch = jest.fn(() =>
    Promise.resolve({
        status: 200,
        ok: true,
        json: () => Promise.resolve({}),
    } as Response),
);

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("Tags", () => {
    test("Should enter an error state when value is longer than 8000 characters.", () => {
        fc.assert(
            fc.property(fc.string({ minLength: 8001 }), (tag) => {
                cleanup();
                console.log(tag.length);
                const { container } = render(
                    <ThemeProvider theme={materialTheme}>
                        <Tags
                            fieldOwner={{
                                fieldValues: {
                                    tags: [
                                        {
                                            value: tag,
                                            uri: Optional.empty<string>(),
                                            vocabulary: Optional.empty<string>(),
                                            version: Optional.empty<string>(),
                                        },
                                    ],
                                },
                                isFieldEditable: () => true,
                                setFieldsDirty: () => {},
                                canChooseWhichToEdit: false,
                                setFieldEditable: () => {},
                                noValueLabel: {
                                    tags: "",
                                },
                            }}
                        />
                    </ThemeProvider>,
                );

                expect(container).toHaveTextContent("Tags must be no longer than 8000 characters.");
            }),
            { numRuns: 10 },
        );
    });
});
