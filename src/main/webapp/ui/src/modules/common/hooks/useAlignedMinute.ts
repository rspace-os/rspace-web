import { Temporal } from "@js-temporal/polyfill";
import { useEffect, useState } from "react";

const now = () => Temporal.Now.instant();
const alignedMinute = (instant: Temporal.Instant) => Math.floor(instant.epochMilliseconds / 60_000) * 60_000;

/** Epoch milliseconds of the current minute's start, updated on each minute boundary. */
export function useAlignedMinute(clock: () => Temporal.Instant = now): number {
  const [minute, setMinute] = useState(() => alignedMinute(clock()));
  useEffect(() => {
    let timeout: ReturnType<typeof setTimeout>;
    const schedule = () => {
      const current = clock();
      timeout = setTimeout(
        () => {
          setMinute(alignedMinute(clock()));
          schedule();
        },
        60_000 - (current.epochMilliseconds % 60_000),
      );
    };
    schedule();
    return () => clearTimeout(timeout);
  }, [clock]);
  return minute;
}
