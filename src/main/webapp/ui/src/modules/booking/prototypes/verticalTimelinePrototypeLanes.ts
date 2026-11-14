// PROTOTYPE ONLY. Shared lane placement for the Codex vertical timeline stories.
import type { DayTimelineEvent } from "@/modules/booking/components/DayTimeline";

export function positionPrototypeEvents(events: readonly DayTimelineEvent[]) {
  const positioned: Array<{ event: DayTimelineEvent; lane: number; lanes: number }> = [];
  let groupStart = 0;
  let groupEnd = Number.NEGATIVE_INFINITY;
  let laneEnds: number[] = [];
  const finishGroup = () => {
    for (let index = groupStart; index < positioned.length; index++) {
      positioned[index].lanes = laneEnds.length;
    }
    groupStart = positioned.length;
    laneEnds = [];
  };
  for (const event of [...events].sort(
    (a, b) => a.startMinute - b.startMinute || a.endMinute - b.endMinute || a.id.localeCompare(b.id),
  )) {
    if (event.startMinute >= groupEnd) finishGroup();
    const available = laneEnds.findIndex((end) => end <= event.startMinute);
    const lane = available < 0 ? laneEnds.length : available;
    laneEnds[lane] = event.endMinute;
    groupEnd = Math.max(groupEnd, event.endMinute);
    positioned.push({ event, lane, lanes: 1 });
  }
  finishGroup();
  return positioned;
}
