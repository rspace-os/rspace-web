import { render, screen } from "@testing-library/react";
import { afterAll, beforeAll, expect, it, vi } from "vitest";
import { VerticalDayTimeline } from "./VerticalDayTimeline";

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

beforeAll(() => vi.stubGlobal("ResizeObserver", ResizeObserverStub));
afterAll(() => vi.unstubAllGlobals());

it("shades the vertical day extent without intercepting input", () => {
  const props = { onDateChange: vi.fn(), timezone: "UTC", events: [], schedule: { status: "success" as const } };
  const { rerender } = render(<VerticalDayTimeline {...props} date="2000-01-01" />);
  const shade = () => screen.getByTestId("vertical-day-timeline-canvas").querySelector('[data-past-time="vertical"]');
  expect(shade()).toHaveStyle({ height: "100%" });
  expect(shade()).toHaveClass("pointer-events-none");
  rerender(<VerticalDayTimeline {...props} date="2099-01-01" />);
  expect(shade()).toHaveStyle({ height: "0%" });
});
