import React from "react";
import { isoToLocale } from "@/util/Util";

type UserDetailsArgs = {
  time: string;
  formatter?: (
    value: number,
    unit: Intl.RelativeTimeFormatUnit,
    suffix: "ago" | "from now",
  ) => string;
};

const THIRTY_DAYS_IN_MS = 30 * 24 * 60 * 60 * 1000;
const ONE_MINUTE_IN_MS = 60 * 1000;
const relativeTimeFormatter = new Intl.RelativeTimeFormat("en-US", {
  numeric: "auto",
  style: "long",
});
const relativeTimeUnits = [
  {
    name: "year",
    milliseconds: 365 * 24 * 60 * 60 * 1000,
  },
  {
    name: "month",
    milliseconds: 30 * 24 * 60 * 60 * 1000,
  },
  {
    name: "day",
    milliseconds: 24 * 60 * 60 * 1000,
  },
  {
    name: "hour",
    milliseconds: 60 * 60 * 1000,
  },
  {
    name: "minute",
    milliseconds: ONE_MINUTE_IN_MS,
  },
] as const satisfies ReadonlyArray<{
  name: Intl.RelativeTimeFormatUnit;
  milliseconds: number;
}>;

function getMillisecondsUntilNextChange(
  elapsedMilliseconds: number,
  unitMilliseconds: number,
): number {
  const remainder = elapsedMilliseconds % unitMilliseconds;
  return remainder === 0 ? unitMilliseconds : unitMilliseconds - remainder;
}

function formatRelativeTime(
  value: number,
  unit: Intl.RelativeTimeFormatUnit,
  suffix: "ago" | "from now",
  formatter?: UserDetailsArgs["formatter"],
): string {
  if (unit === "second" && suffix === "ago") {
    return "< 1 minute ago";
  }

  if (typeof formatter === "function") {
    return formatter(value, unit, suffix);
  }

  return relativeTimeFormatter.format(suffix === "ago" ? -value : value, unit);
}

function getDisplayState(
  targetDate: Date,
  nowMilliseconds: number,
  formatter?: UserDetailsArgs["formatter"],
): { text: string; nextUpdateInMilliseconds: number | null } {
  const targetMilliseconds = targetDate.getTime();

  if (targetMilliseconds + THIRTY_DAYS_IN_MS <= nowMilliseconds) {
    return {
      text: isoToLocale(targetDate.toISOString()),
      nextUpdateInMilliseconds: null,
    };
  }

  if (targetMilliseconds > nowMilliseconds) {
    const remainingMilliseconds = targetMilliseconds - nowMilliseconds;
    const { name, milliseconds } =
      relativeTimeUnits.find(
        ({ milliseconds: unitMilliseconds }) =>
          remainingMilliseconds >= unitMilliseconds,
      ) ?? {
        name: "second" as const,
        milliseconds: 1000,
      };
    const value =
      name === "second"
        ? Math.max(1, Math.ceil(remainingMilliseconds / milliseconds))
        : Math.max(1, Math.floor(remainingMilliseconds / milliseconds));
    return {
      text: formatRelativeTime(value, name, "from now", formatter),
      nextUpdateInMilliseconds:
        name === "second"
          ? Math.max(1, remainingMilliseconds % milliseconds || milliseconds)
          : Math.max(
              1,
              remainingMilliseconds -
                Math.floor(remainingMilliseconds / milliseconds) * milliseconds,
            ),
    };
  }

  const elapsedMilliseconds = nowMilliseconds - targetMilliseconds;

  if (elapsedMilliseconds < ONE_MINUTE_IN_MS) {
    return {
      text: formatRelativeTime(0, "second", "ago", formatter),
      nextUpdateInMilliseconds: 1000,
    };
  }

  const { name, milliseconds } =
    relativeTimeUnits.find(
      ({ milliseconds: unitMilliseconds }) =>
        elapsedMilliseconds >= unitMilliseconds,
    ) ?? relativeTimeUnits[relativeTimeUnits.length - 1];
  const value = Math.floor(elapsedMilliseconds / milliseconds);
  return {
    text: formatRelativeTime(value, name, "ago", formatter),
    nextUpdateInMilliseconds: Math.min(
      getMillisecondsUntilNextChange(elapsedMilliseconds, milliseconds),
      Math.max(
        1,
        THIRTY_DAYS_IN_MS - elapsedMilliseconds,
      ),
    ),
  };
}

const TimeAgoCustom = ({ time, formatter }: UserDetailsArgs) => {
  const targetDate = React.useMemo(() => new Date(time), [time]);
  const [nowMilliseconds, setNowMilliseconds] = React.useState(() => Date.now());

  React.useEffect(() => {
    setNowMilliseconds(Date.now());
  }, [time]);

  const { text, nextUpdateInMilliseconds } = React.useMemo(
    () => getDisplayState(targetDate, nowMilliseconds, formatter),
    [formatter, nowMilliseconds, targetDate],
  );

  React.useEffect(() => {
    if (nextUpdateInMilliseconds === null) {
      return undefined;
    }
    const timeout = window.setTimeout(() => {
      setNowMilliseconds(Date.now());
    }, nextUpdateInMilliseconds);
    return () => {
      window.clearTimeout(timeout);
    };
  }, [nextUpdateInMilliseconds]);

  return <time dateTime={time}>{text}</time>;
}

export default TimeAgoCustom;
