// PROTOTYPE ONLY. Compare edge grips, time chips and thin edges before changing production handles.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { useState } from "react";
import { timelineAdjuster, timelinePolicy } from "@/modules/booking/creation/timelineRange";
import { dayMinuteToZonedTime, instantToDayMinute } from "@/modules/booking/domain/bookingTime";
import { SCENARIO_IDS, SCENARIOS } from "./verticalDayTimelineOpusFixtures";

function Example({
  vertical,
  short,
  variant,
}: {
  vertical: boolean;
  short: boolean;
  variant: "grip" | "chip" | "line";
}) {
  const fixture = SCENARIOS[SCENARIO_IDS[0]];
  const [blocked, setBlocked] = useState(false);
  const [range, setRange] = useState(() => ({
    start: fixture.draftStart,
    end: short
      ? dayMinuteToZonedTime(
          fixture.date,
          fixture.displayTimezone,
          instantToDayMinute(fixture.draftStart, fixture.date, fixture.displayTimezone) + 15,
        )
          .toInstant()
          .toString()
      : fixture.draftEnd,
  }));
  const adjuster = timelineAdjuster(range, {
    date: fixture.date,
    displayTimezone: fixture.displayTimezone,
    policy: timelinePolicy(fixture.item),
  });
  const start = instantToDayMinute(range.start, fixture.date, fixture.displayTimezone);
  const end = instantToDayMinute(range.end, fixture.date, fixture.displayTimezone);
  const origin = 360;
  const span = 900;
  return (
    <div className="space-y-2">
      <p>
        {vertical ? "Vertical" : "Horizontal"}, {short ? "15 minute interval" : "long interval"}
      </p>
      <div
        className="relative rounded border bg-muted"
        style={vertical ? { height: 420, width: 160 } : { height: 100, width: 560 }}
      >
        <div
          className="absolute rounded bg-primary/20"
          style={
            vertical
              ? {
                  top: `${((start - origin) / span) * 100}%`,
                  height: `${((end - start) / span) * 100}%`,
                  left: 30,
                  right: 30,
                }
              : {
                  left: `${((start - origin) / span) * 100}%`,
                  width: `${((end - start) / span) * 100}%`,
                  top: 25,
                  bottom: 25,
                }
          }
        >
          <span className="absolute inset-0 grid place-items-center text-xs">Move</span>
          {(["start", "end"] as const).map((edge) => (
            <button
              key={edge}
              type="button"
              aria-label={`${edge} time`}
              aria-disabled={!adjuster?.canResize(edge)}
              className="absolute z-10 grid min-h-11 min-w-11 touch-none place-items-center rounded focus-visible:outline-2 focus-visible:outline-ring hover:bg-primary/10 aria-disabled:opacity-40"
              style={
                vertical
                  ? {
                      left: "50%",
                      transform: "translateX(-50%)",
                      ...(edge === "start" ? { bottom: "100%" } : { top: "100%" }),
                    }
                  : {
                      top: "50%",
                      transform: "translateY(-50%)",
                      ...(edge === "start" ? { right: "100%" } : { left: "100%" }),
                    }
              }
              onKeyDown={(event) => {
                if (variant !== "grip" || !["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key))
                  return;
                event.preventDefault();
                const next = adjuster?.step(
                  edge,
                  event.key === "ArrowLeft" || event.key === "ArrowUp" ? "previous" : "next",
                );
                setBlocked(next === undefined);
                if (next) setRange(next);
              }}
              onPointerDown={(event) => {
                if (variant !== "grip") return;
                event.currentTarget.setPointerCapture(event.pointerId);
              }}
              onPointerMove={(event) => {
                if (variant !== "grip" || !event.currentTarget.hasPointerCapture(event.pointerId)) return;
                const track = event.currentTarget.parentElement?.parentElement?.getBoundingClientRect();
                if (!track) return;
                const minute =
                  origin +
                  (vertical ? (event.clientY - track.top) / track.height : (event.clientX - track.left) / track.width) *
                    span;
                const next = adjuster?.adjust(
                  edge,
                  dayMinuteToZonedTime(fixture.date, fixture.displayTimezone, Math.round(minute)).toInstant(),
                );
                setBlocked(next === undefined);
                if (next) setRange(next);
              }}
            >
              {variant === "grip" ? (
                <span className={vertical ? "h-1 w-6 rounded bg-primary" : "h-6 w-1 rounded bg-primary"} />
              ) : variant === "chip" ? (
                <span className="rounded border bg-background px-1 text-xs">{range[edge].slice(11, 16)}</span>
              ) : (
                <span className={vertical ? "h-px w-full bg-primary hover:h-1" : "h-full w-px bg-primary hover:w-1"} />
              )}
            </button>
          ))}
        </div>
      </div>
      <p role="status" className="text-xs text-muted-foreground">
        {blocked
          ? "That adjustment is blocked by the increment, maximum duration or opening hours."
          : "Use arrow keys or drag the grip to adjust the interval."}
      </p>
      <output className="block text-xs">{JSON.stringify(range)}</output>
    </div>
  );
}
function Prototype() {
  return (
    <div className="space-y-8 p-8">
      {(["grip", "chip", "line"] as const).map((variant) => (
        <section key={variant} className="space-y-4">
          <h2>{variant}</h2>
          {[false, true].map((vertical) => (
            <div key={String(vertical)} className="flex flex-wrap gap-8">
              {[false, true].map((short) => (
                <Example key={String(short)} vertical={vertical} short={short} variant={variant} />
              ))}
            </div>
          ))}
        </section>
      ))}
    </div>
  );
}
const meta = { title: "Booking/Prototypes/Timeline drag handles", component: Prototype } satisfies Meta<
  typeof Prototype
>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Comparison: Story = {};
