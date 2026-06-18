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
