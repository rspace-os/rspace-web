/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import { useContext, useEffect } from "react";
import "@testing-library/jest-dom";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import Alerts from "../Alerts";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

function DisplaysAlert() {
    const { addAlert } = useContext(AlertContext);

    useEffect(() => {
        addAlert(mkAlert({ message: "Success!" }));
    }, [addAlert]);

    return <></>;
}

describe("Alerts", () => {
    test("Example of usage", () => {
        render(
            <Alerts>
                <DisplaysAlert />
            </Alerts>,
        );

        expect(screen.getByRole("alert")).toBeVisible();
        expect(screen.getByText("Success!")).toBeVisible();
    });
});
