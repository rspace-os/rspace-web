import { test, describe, expect, beforeEach, vi } from 'vitest';
import React,
  { useContext,
  useEffect } from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import Alerts from "../Alerts";

beforeEach(() => {
  vi.clearAllMocks();
});


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


