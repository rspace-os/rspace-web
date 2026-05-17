import { afterEach, describe, expect, test, vi } from "vitest";
import { act, render, screen } from "@/__tests__/customQueries";
import { isoToLocale } from "@/util/Util";
import TimeAgoCustom from "../TimeAgoCustom";

describe("TimeAgoCustom", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  test("renders recent timestamps as relative time and refreshes automatically", () => {
    const now = new Date("2026-05-17T16:30:00.000Z");
    const time = "2026-05-17T16:29:01.000Z";
    vi.useFakeTimers();
    vi.setSystemTime(now);

    render(<TimeAgoCustom time={time} />);

    expect(screen.getByText("< 1 minute ago")).toBeVisible();

    act(() => {
      vi.advanceTimersByTime(1_000);
    });

    expect(screen.getByText("1 minute ago")).toBeVisible();
  });

  test("supports custom relative-time formatters", () => {
    const now = new Date("2026-05-17T16:30:00.000Z");
    const time = "2026-05-17T15:00:00.000Z";
    vi.useFakeTimers();
    vi.setSystemTime(now);

    render(
      <TimeAgoCustom
        time={time}
        formatter={(value, unit, suffix) => `${value}${unit[0]} ${suffix}`}
      />,
    );

    expect(screen.getByText("1h ago")).toBeVisible();
  });

  test("falls back to an absolute date for old timestamps", () => {
    const now = new Date("2026-05-17T16:30:00.000Z");
    const time = "2026-04-16T16:30:00.000Z";
    vi.useFakeTimers();
    vi.setSystemTime(now);

    render(<TimeAgoCustom time={time} />);

    expect(screen.getByText(isoToLocale(time))).toBeVisible();
  });
});
