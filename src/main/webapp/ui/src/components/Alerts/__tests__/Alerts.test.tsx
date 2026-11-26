import { act, render, screen } from "@testing-library/react";
import { type ContextType, useContext, useEffect } from "react";
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

  test("adds alerts in order and removes only the one dismissed, through a context value that stays the same", () => {
    const contextValues: Array<ContextType<typeof AlertContext>> = [];
    function CapturesContext() {
      contextValues.push(useContext(AlertContext));
      return null;
    }
    render(
      <Alerts>
        <CapturesContext />
      </Alerts>,
    );
    const { addAlert, removeAlert } = contextValues[0];
    const first = mkAlert({ message: "First", isInfinite: true });
    const second = mkAlert({ message: "Second", isInfinite: true });

    act(() => {
      addAlert(first);
      addAlert(second);
    });
    expect(screen.getAllByRole("alert").map((alert) => alert.textContent)).toEqual([
      expect.stringContaining("First"),
      expect.stringContaining("Second"),
    ]);

    act(() => {
      removeAlert(first);
    });
    expect(screen.queryByText("First")).not.toBeInTheDocument();
    expect(screen.getByText("Second")).toBeVisible();
    expect(new Set(contextValues).size).toBe(1);
  });
});
