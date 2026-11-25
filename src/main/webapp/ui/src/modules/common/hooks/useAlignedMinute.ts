import { useEffect, useState } from "react";

const now = () => new Date();
const alignedMinute = (date: Date) => Math.floor(date.getTime() / 60_000) * 60_000;

export function useAlignedMinute(clock: () => Date = now): number {
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
        60_000 - (current.getTime() % 60_000),
      );
    };
    schedule();
    return () => clearTimeout(timeout);
  }, [clock]);
  return minute;
}
