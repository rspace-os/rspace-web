import { render, screen } from "@testing-library/react";
import { useContext, useEffect } from "react";
import { describe, expect, test } from "vitest";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";

import Alerts from "../Alerts";

function DisplaysAlert() {
  const { addAlert } = useContext(AlertContext);
  useEffect(() => {
    addAlert(mkAlert({ message: "Success!" }));
  }, []);
  // biome-ignore lint/complexity/noUselessFragments: initial biome migration
  return <></>;
}
function DisplaysAlertWithElementIcon() {
  const { addAlert } = useContext(AlertContext);
  useEffect(() => {
    addAlert(mkAlert({ message: "Frozen!", icon: <span data-testid="custom-icon" /> }));
  }, []);
  // biome-ignore lint/complexity/noUselessFragments: matches the sibling helper
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

  test("renders an alert carrying a React element icon", () => {
    // Pins the shallow-observation fix: deep-proxying a React element icon (useLocalObservable)
    // crashed React 19 dev; the store must keep such alerts renderable.
    render(
      <Alerts>
        <DisplaysAlertWithElementIcon />
      </Alerts>,
    );
    expect(screen.getByRole("alert")).toBeVisible();
    expect(screen.getByTestId("custom-icon")).toBeVisible();
  });
});
