import type { DayTimelineEvent } from "./DayTimelineEvent";

export type PositionedVerticalTimelineEvent = {
  event: DayTimelineEvent;
  startMinute: number;
  endMinute: number;
  lane: number;
  laneCount: number;
  continuesBefore: boolean;
  continuesAfter: boolean;
};

/** Clip rendered event segments to one displayed day and assign stable lanes to each overlap group. */
export function layoutVerticalTimelineEvents(
  events: readonly DayTimelineEvent[],
  dayMinutes: number,
): PositionedVerticalTimelineEvent[] {
  if (!Number.isFinite(dayMinutes) || dayMinutes <= 0) return [];

  const sorted = events
    .filter(
      ({ startMinute, endMinute }) =>
        Number.isFinite(startMinute) &&
        Number.isFinite(endMinute) &&
        endMinute > startMinute &&
        endMinute > 0 &&
        startMinute < dayMinutes,
    )
    .toSorted(
      (left, right) =>
        left.startMinute - right.startMinute || left.endMinute - right.endMinute || left.id.localeCompare(right.id),
    );

  const positioned: PositionedVerticalTimelineEvent[] = [];
  let groupStart = 0;
  let groupEnd = Number.NEGATIVE_INFINITY;
  let laneEnds: number[] = [];

  const finishGroup = () => {
    for (let index = groupStart; index < positioned.length; index += 1) {
      positioned[index].laneCount = laneEnds.length;
    }
    groupStart = positioned.length;
    groupEnd = Number.NEGATIVE_INFINITY;
    laneEnds = [];
  };

  for (const event of sorted) {
    if (event.startMinute >= groupEnd) finishGroup();

    const lane = laneEnds.findIndex((endMinute) => endMinute <= event.startMinute);
    const assignedLane = lane < 0 ? laneEnds.length : lane;
    laneEnds[assignedLane] = event.endMinute;
    groupEnd = Math.max(groupEnd, event.endMinute);

    positioned.push({
      event,
      startMinute: Math.max(0, event.startMinute),
      endMinute: Math.min(dayMinutes, event.endMinute),
      lane: assignedLane,
      laneCount: 1,
      continuesBefore: event.startMinute < 0,
      continuesAfter: event.endMinute > dayMinutes,
    });
  }

  finishGroup();
  return positioned;
}
