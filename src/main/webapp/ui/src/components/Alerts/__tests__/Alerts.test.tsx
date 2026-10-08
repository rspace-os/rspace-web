import { render, screen } from "@testing-library/react";
import { useContext, useEffect } from "react";
import { describe, expect, test } from "vitest";
import AlertContext, { type Alert, mkAlert } from "../../../stores/contexts/Alert";

import Alerts from "../Alerts";

function DisplaysAlert({ alert }: { alert: Alert }) {
  const { addAlert } = useContext(AlertContext);
  useEffect(() => {
    addAlert(alert);
  }, []);
  // biome-ignore lint/complexity/noUselessFragments: initial biome migration
  return <></>;
}

describe("Alerts", () => {
  test("Example of usage", () => {
    render(
      <Alerts>
        <DisplaysAlert alert={mkAlert({ message: "Success!" })} />
      </Alerts>,
    );
    expect(screen.getByRole("alert")).toBeVisible();
    expect(screen.getByText("Success!")).toBeVisible();
  });

  test("renders an alert carrying a React element icon", () => {
    render(
      <Alerts>
        <DisplaysAlert alert={mkAlert({ message: "Frozen!", icon: <span data-testid="custom-icon" /> })} />
      </Alerts>,
    );
    expect(screen.getByRole("alert")).toBeVisible();
    expect(screen.getByTestId("custom-icon")).toBeVisible();
  });
});
