import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { page } from "vitest/browser";
import FormSectionsContext from "@/stores/contexts/FormSections";
import materialTheme from "@/theme";
import { StepperPanelHeader } from "./StepperPanelHeader";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

test("unmounting cancels the pending expand-all timeout", async () => {
  const schedule = vi.spyOn(window, "setTimeout");
  const cancel = vi.spyOn(window, "clearTimeout");
  const { unmount } = render(
    <ThemeProvider theme={materialTheme}>
      <FormSectionsContext.Provider value={{ isExpanded: () => true, setExpanded: () => {}, setAllExpanded: () => {} }}>
        <StepperPanelHeader onToggle={() => {}} open title="Details" id="details" recordType="container" />
      </FormSectionsContext.Provider>
    </ThemeProvider>,
  );
  await page.getByRole("button", { name: "Collapse section", exact: true }).click();
  const index = schedule.mock.calls.findIndex(([, delay]) => delay === 5000);
  expect(index).toBeGreaterThanOrEqual(0);
  const timer = schedule.mock.results[index].value;
  unmount();
  expect(cancel).toHaveBeenCalledWith(timer);
});
