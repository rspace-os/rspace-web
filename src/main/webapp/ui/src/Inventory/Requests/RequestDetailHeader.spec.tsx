import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, onTestFinished, test } from "vitest";
import { page } from "vitest/browser";
import { makeMockRootStore } from "@/stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import RequestDetailHeader from "./RequestDetailHeader";

afterEach(() => {
  cleanup();
});

test.each([
  ["with spaces", "Long sample name ".repeat(15).slice(0, 255)],
  ["without spaces", "x".repeat(255)],
])("clamps a maximum-length sample name %s to two lines on a phone-width viewport", async (_, sampleName) => {
  const { innerWidth, innerHeight } = window;
  await page.viewport(390, 844);
  onTestFinished(() => page.viewport(innerWidth, innerHeight));

  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={makeMockRootStore({ uiStore: { setVisiblePanel: () => {} } })}>
        <RequestDetailHeader id={1} sampleName={sampleName} status="PENDING" />
      </storesContext.Provider>
    </ThemeProvider>,
  );

  const heading = page.getByRole("heading").element();
  const lineHeight = Number.parseFloat(getComputedStyle(heading).lineHeight);
  expect(heading.getBoundingClientRect().height).toBeLessThanOrEqual(2 * lineHeight + 1);
  expect(heading.scrollWidth).toBeLessThanOrEqual(heading.clientWidth);
});
