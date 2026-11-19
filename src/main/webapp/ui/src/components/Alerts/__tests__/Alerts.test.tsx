import { render, screen } from "@testing-library/react";
import userEvent, { PointerEventsCheckLevel } from "@testing-library/user-event";
import { useContext, useEffect } from "react";
import { describe, expect, test, vi } from "vitest";
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

  test("runs a retry once however often Retry is clicked while it runs", async () => {
    // the second click is sent, as a hurried user's would be, rather than refused by the pointer-events check
    const user = userEvent.setup({ pointerEventsCheck: PointerEventsCheckLevel.Never });
    const { promise, resolve } = Promise.withResolvers<void>();
    const retryFunction = vi.fn(() => promise);
    render(
      <Alerts>
        <DisplaysAlert alert={mkAlert({ message: "Save failed", variant: "error", retryFunction })} />
      </Alerts>,
    );
    const retry = screen.getByRole("button", { name: "common:actions.retry" });

    await user.click(retry);
    await user.click(retry);

    expect(retry).toBeDisabled();
    expect(retryFunction).toHaveBeenCalledOnce();
    resolve();
  });
});
