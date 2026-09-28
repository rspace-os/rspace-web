import { cleanup, render } from "@testing-library/react";
import { useContext, useEffect } from "react";
import { afterEach, describe, expect, test } from "vitest";
import { type Locator, page } from "vitest/browser";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import Alerts from "../Alerts";

function ShowsWarningWithAWrappingMessage() {
  const { addAlert } = useContext(AlertContext);
  useEffect(() => {
    addAlert(
      mkAlert({
        variant: "warning",
        isInfinite: true,
        title: "Some registry entries were not imported",
        message:
          "2 entries of the registry record could not be linked from the new instrument. Each is listed here with the reason.",
        details: [
          { variant: "warning", title: "Measurement technique" },
          { variant: "warning", title: "Calibration" },
        ],
      }),
    );
  }, [addAlert]);
  return null;
}

// the variant icon is decorative and aria-hidden, so there is no role or name to find it by
function cssWithin(root: Locator, selector: string): Locator {
  return (root as unknown as { locator(s: string): Locator }).locator(`css=${selector}`);
}

afterEach(() => {
  cleanup();
});

describe("Alerts", () => {
  test("keeps the variant icon clear of the title when the message wraps", async () => {
    render(
      <Alerts>
        <ShowsWarningWithAWrappingMessage />
      </Alerts>,
    );
    const toast = page.getByRole("group", { name: "warning alert" });
    const title = toast.getByText("Some registry entries were not imported");
    await expect.element(title).toBeVisible();

    // a wrapping message squeezed the icon's cell until the white icon touched the white title
    const icon = cssWithin(toast, 'svg[data-testid="WarningIcon"]');
    const gap = title.element().getBoundingClientRect().left - icon.element().getBoundingClientRect().right;
    expect(gap).toBeGreaterThanOrEqual(8);
  });
});
