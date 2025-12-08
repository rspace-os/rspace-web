/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import { axe, toHaveNoViolations } from "jest-axe";
import materialTheme from "../../../../theme";
import DataciteCard from "../DataciteCard";

expect.extend(toHaveNoViolations);

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("DataciteCard", () => {
    test("Should have no axe violations.", async () => {
        const { container } = render(
            <ThemeProvider theme={materialTheme}>
                <DataciteCard
                    currentSettings={{
                        enabled: "true",
                        serverUrl: "https://api.datacite.org",
                        username: "",
                        password: "",
                        repositoryPrefix: "",
                    }}
                />
            </ThemeProvider>,
        );

        expect(await axe(container)).toHaveNoViolations();
    });
});
