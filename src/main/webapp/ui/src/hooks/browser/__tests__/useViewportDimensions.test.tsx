/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import type React from "react";
import "@testing-library/jest-dom";
import useViewportDimensions from "../useViewportDimensions";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

function Wrapper(): React.ReactNode {
    const { width } = useViewportDimensions();
    return <>{width}</>;
}

describe("useViewportDimensions", () => {
    test("Inside of a test environment, width defaults to 1024px.", () => {
        render(<Wrapper />);
        expect(screen.getByText("1024")).toBeVisible();
    });
});
