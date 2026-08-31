export type AvailabilityInterval = {
  kind: "booking" | "blockout";
  startsAt: Date;
  endsAt: Date;
};

export type AvailabilityState = "available" | "booking" | "blockout" | "overlap";

export type AvailabilitySegment = {
  startsAt: number;
  endsAt: number;
  state: AvailabilityState;
};

export type AvailabilitySource = {
  id: string;
  startsAt: Date;
  endsAt: Date;
};

export type SourcedAvailabilityInterval = AvailabilityInterval & {
  source: AvailabilitySource;
};

export type AvailabilitySlice<T extends AvailabilityInterval = AvailabilityInterval> = AvailabilitySegment & {
  intervals: readonly T[];
};

export type CurrentDayAvailability = "available-now" | "free-later-today" | "unavailable-today";

type TimestampInterval = Omit<AvailabilitySegment, "state">;

function timestamp(date: Date, name: string): number {
  const value = date.getTime();
  if (!Number.isFinite(value)) throw new RangeError(`${name} must be a valid Date`);
  return value;
}

function mergeIntervals(intervals: readonly TimestampInterval[]): TimestampInterval[] {
  const merged: TimestampInterval[] = [];
  for (const interval of intervals.toSorted(
    (left, right) => left.startsAt - right.startsAt || left.endsAt - right.endsAt,
  )) {
    const previous = merged.at(-1);
    if (previous && interval.startsAt <= previous.endsAt) previous.endsAt = Math.max(previous.endsAt, interval.endsAt);
    else merged.push({ ...interval });
  }
  return merged;
}

function buildTimeline<T extends AvailabilityInterval>(
  intervals: readonly T[],
  periodStart: Date,
  periodEnd: Date,
): { clipped: T[]; segments: AvailabilitySegment[] } {
  const start = timestamp(periodStart, "periodStart");
  const end = timestamp(periodEnd, "periodEnd");
  if (end <= start) throw new RangeError("periodEnd must be after periodStart");
  const clipped = intervals.flatMap((interval) => {
    const startsAt = timestamp(interval.startsAt, "interval startsAt");
    const endsAt = timestamp(interval.endsAt, "interval endsAt");
    if (endsAt <= startsAt) throw new RangeError("interval endsAt must be after startsAt");
    if (endsAt <= start || startsAt >= end) return [];
    return [
      {
        ...interval,
        startsAt: new Date(Math.max(startsAt, start)),
        endsAt: new Date(Math.min(endsAt, end)),
      },
    ];
  });
  const timestampIntervals = clipped.map(({ kind, startsAt, endsAt }) => ({
    kind,
    startsAt: startsAt.getTime(),
    endsAt: endsAt.getTime(),
  }));
  const bookings = mergeIntervals(timestampIntervals.filter(({ kind }) => kind === "booking"));
  const blockouts = mergeIntervals(timestampIntervals.filter(({ kind }) => kind === "blockout"));
  const boundaries = [
    ...new Set([
      start,
      end,
      ...bookings.flatMap(({ startsAt, endsAt }) => [startsAt, endsAt]),
      ...blockouts.flatMap(({ startsAt, endsAt }) => [startsAt, endsAt]),
    ]),
  ].toSorted((left, right) => left - right);

  const segments: AvailabilitySegment[] = boundaries.slice(0, -1).map((startsAt, index) => {
    const endsAt = boundaries[index + 1];
    const booked = bookings.some((interval) => interval.startsAt < endsAt && interval.endsAt > startsAt);
    const blocked = blockouts.some((interval) => interval.startsAt < endsAt && interval.endsAt > startsAt);
    return {
      startsAt,
      endsAt,
      state: booked && blocked ? "overlap" : booked ? "booking" : blocked ? "blockout" : "available",
    };
  });
  return { clipped, segments };
}

export function buildAvailabilitySegments(
  intervals: readonly AvailabilityInterval[],
  periodStart: Date,
  periodEnd: Date,
): AvailabilitySegment[] {
  return buildTimeline(intervals, periodStart, periodEnd).segments;
}

export function buildAvailabilitySlices<T extends AvailabilityInterval>(
  intervals: readonly T[],
  periodStart: Date,
  periodEnd: Date,
): AvailabilitySlice<T>[] {
  const { clipped, segments } = buildTimeline(intervals, periodStart, periodEnd);
  return segments.map((segment) => ({
    ...segment,
    intervals:
      segment.state === "available"
        ? []
        : clipped.filter(
            (interval) => interval.startsAt.getTime() < segment.endsAt && interval.endsAt.getTime() > segment.startsAt,
          ),
  }));
}

export function classifyCurrentDayAvailability(
  intervals: readonly AvailabilityInterval[],
  dayStart: Date,
  dayEnd: Date,
  now: Date,
): CurrentDayAvailability {
  const segments = buildAvailabilitySegments(intervals, dayStart, dayEnd);
  const current = timestamp(now, "now");
  if (current < dayStart.getTime() || current >= dayEnd.getTime()) throw new RangeError("now must be within the day");
  const currentIndex = segments.findIndex(({ startsAt, endsAt }) => startsAt <= current && current < endsAt);
  if (segments[currentIndex]?.state === "available") return "available-now";
  return segments
    .slice(currentIndex + 1)
    .some(({ startsAt, endsAt, state }) => state === "available" && endsAt > startsAt)
    ? "free-later-today"
    : "unavailable-today";
}
