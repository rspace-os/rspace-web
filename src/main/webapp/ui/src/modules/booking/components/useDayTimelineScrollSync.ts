import { useEffect, useState } from "react";

/** Share an elapsed-minute viewport without rendering React content on scroll. */
export function useDayTimelineScrollSync() {
  const [sync] = useState(() => {
    const rows = new Map<HTMLElement, { dayMinutes: number; lastLeft: number }>();
    let centerMinute: number | undefined;
    let frame: number | undefined;
    let source: HTMLElement | undefined;
    return {
      get centerMinute() {
        return centerMinute;
      },
      register(element: HTMLElement, dayMinutes: number, center: number) {
        centerMinute = center;
        rows.set(element, { dayMinutes, lastLeft: element.scrollLeft });
        return () => {
          rows.delete(element);
        };
      },
      scroll(element: HTMLElement) {
        const row = rows.get(element);
        if (!row || element.scrollWidth === 0 || Math.abs(element.scrollLeft - row.lastLeft) < 0.5) return;
        row.lastLeft = element.scrollLeft;
        centerMinute = ((element.scrollLeft + element.clientWidth / 2) / element.scrollWidth) * row.dayMinutes;
        source = element;
        if (frame !== undefined) return;
        frame = requestAnimationFrame(() => {
          frame = undefined;
          if (centerMinute === undefined || !source || !rows.has(source)) return;
          const center = centerMinute;
          // Read all geometry before writing; ignore scroll events caused by these writes.
          const targets = Array.from(rows, ([target, state]) => ({
            target,
            state,
            left: Math.max(
              0,
              Math.min(
                target.scrollWidth - target.clientWidth,
                (center / state.dayMinutes) * target.scrollWidth - target.clientWidth / 2,
              ),
            ),
          }));
          for (const { target, state, left } of targets) {
            if (target === source) continue;
            target.scrollLeft = left;
            state.lastLeft = target.scrollLeft;
          }
        });
      },
      cancel() {
        if (frame !== undefined) cancelAnimationFrame(frame);
        frame = undefined;
      },
    };
  });
  useEffect(() => () => sync.cancel(), [sync]);
  return sync;
}
