import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { isoToLocale } from "@/util/Util";
import TimeAgoCustom from "../TimeAgoCustom";

const NOW = new Date("2026-07-03T12:00:00.000Z");

describe("TimeAgoCustom", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  test("renders the existing long relative text by default", () => {
    render(<TimeAgoCustom time="2026-07-03T11:58:00.000Z" />);
    expect(screen.getByText("2 minutes ago")).toBeInTheDocument();
  });

  test("renders the custom text for durations under a minute", () => {
    render(<TimeAgoCustom time="2026-07-03T11:59:50.000Z" />);
    expect(screen.getByText("common:timeAgo.lessThanOneMinuteAgo")).toBeInTheDocument();
  });

  test("renders compact relative text with the former formatter behaviour", () => {
    render(<TimeAgoCustom time="2026-07-03T11:58:01.000Z" compact />);
    expect(screen.getByText("1m ago")).toBeInTheDocument();
  });

  test("renders a locale date after the relative-time cutoff", () => {
    const time = "2026-06-01T12:00:00.000Z";
    render(<TimeAgoCustom time={time} />);
    expect(screen.getByText(isoToLocale(time))).toBeInTheDocument();
  });
});
