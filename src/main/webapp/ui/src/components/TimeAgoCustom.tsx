import type React from "react";
import { useEffect, useState } from "react";
import TimeAgo from "react-timeago-i18n";
import { isoToLocale } from "@/util/Util";

type TimeUnit = "year" | "month" | "day" | "hour" | "minute" | "second";
type Formatter = (value: number, unit: TimeUnit, suffix: string) => React.ReactNode;

type UserDetailsArgs = {
  time: string;
  formatter?: Formatter;
};

const UNITS: ReadonlyArray<[TimeUnit, number]> = [
  ["year", 365 * 24 * 60 * 60 * 1000],
  ["month", 30 * 24 * 60 * 60 * 1000],
  ["day", 24 * 60 * 60 * 1000],
  ["hour", 60 * 60 * 1000],
  ["minute", 60 * 1000],
  ["second", 1000],
];

function getRelativeParts(time: string, now: number): { value: number; unit: TimeUnit; suffix: string } {
  const delta = now - Date.parse(time);
  const absDelta = Math.abs(delta);
  const [unit, unitMs] = UNITS.find(([, ms]) => absDelta >= ms) ?? ["second", 1000];
  return {
    value: Math.floor(absDelta / unitMs),
    unit,
    suffix: delta >= 0 ? "ago" : "from now",
  };
}

const TimeAgoCustom = ({ time, formatter }: UserDetailsArgs) => {
  const [now, setNow] = useState(Date.now);

  useEffect(() => {
    if (!formatter) return;
    const interval = window.setInterval(() => setNow(Date.now()), 60 * 1000);
    return () => window.clearInterval(interval);
  }, [formatter]);

  // display "time ago" if less than 30 days ago
  if (Date.parse(time) + 30 * 24 * 60 * 60 * 1000 > Date.now()) {
    if (formatter) {
      const { value, unit, suffix } = getRelativeParts(time, now);
      return <>{unit === "second" ? "< 1 minute ago" : formatter(value, unit, suffix)}</>;
    }
    return <TimeAgo date={time} locale="en-US" hideSecondsText={["< 1 minute ago", "in < 1 minute"]} />;
  }
  // display actual date otherwise
  return <>{isoToLocale(time)}</>;
};

export default TimeAgoCustom;
