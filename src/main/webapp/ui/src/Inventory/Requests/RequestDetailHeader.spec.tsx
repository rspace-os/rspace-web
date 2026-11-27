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

async function renderHeaderAt(width: number, height: number, sampleName: string): Promise<HTMLElement> {
  const { innerWidth, innerHeight } = window;
  await page.viewport(width, height);
  onTestFinished(() => page.viewport(innerWidth, innerHeight));

  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={makeMockRootStore({ uiStore: { setVisiblePanel: () => {} } })}>
        <RequestDetailHeader id={1} sampleName={sampleName} status="PENDING" />
      </storesContext.Provider>
    </ThemeProvider>,
  );
  return page.getByRole("heading").element() as HTMLElement;
}

const MAX_LENGTH_NAMES = [
  ["with spaces", "Long sample name ".repeat(15).slice(0, 255)],
  ["without spaces", "x".repeat(255)],
];

test.each(MAX_LENGTH_NAMES)(
  "clamps a maximum-length sample name %s to two lines on a phone-width viewport",
  async (_, sampleName) => {
    const heading = await renderHeaderAt(390, 844, sampleName);
    const lineHeight = Number.parseFloat(getComputedStyle(heading).lineHeight);
    expect(heading.getBoundingClientRect().height).toBeLessThanOrEqual(2 * lineHeight + 1);
    expect(heading.scrollWidth).toBeLessThanOrEqual(heading.clientWidth);
  },
);

test.each(MAX_LENGTH_NAMES)(
  "shows a maximum-length sample name %s in full, wrapped, on a desktop-width viewport",
  async (_, sampleName) => {
    const heading = await renderHeaderAt(1280, 720, sampleName);
    expect(heading.scrollHeight).toBeLessThanOrEqual(heading.clientHeight);
    expect(heading.scrollWidth).toBeLessThanOrEqual(heading.clientWidth);
    const lineHeight = Number.parseFloat(getComputedStyle(heading).lineHeight);
    expect(heading.getBoundingClientRect().height).toBeGreaterThan(2 * lineHeight + 1);
  },
);
