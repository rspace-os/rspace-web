/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React, { useContext, useEffect } from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import Alert from "../../../stores/models/Alert";
import Alerts from "../Alerts";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

function DisplaysAlert() {
  const { addAlert } = useContext(AlertContext);

  useEffect(() => {
    addAlert(mkAlert({ message: "Success!" }));
  }, []);

  return <></>;
}

describe("Alerts", () => {
  test("Example of usage", () => {
    render(
      <Alerts>
        <DisplaysAlert />
      </Alerts>
    );

    expect(screen.getByRole("alert")).toBeVisible();
    expect(screen.getByText("Success!")).toBeVisible();
  });
});
