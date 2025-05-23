import React from "react";
import TimeAgo from "react-timeago";
import { isoToLocale } from "../util/Util";

type UserDetailsArgs = {
  time: string;
  formatter?: (
    value: number,
    unit: TimeAgo.Unit,
    suffix: TimeAgo.Suffix
  ) => string;
};

export default function UserDetails({
  time,
  formatter,
}: UserDetailsArgs): JSX.Element {
  const customFormatter = (
    value: number,
    unit: TimeAgo.Unit,
    suffix: TimeAgo.Suffix,
    epochMillis: number,
    nextFormatter?: TimeAgo.Formatter
  ): React.ReactNode => {
    if (unit === "second") {
      return "< 1 minute ago";
    }

    if (formatter) {
      return formatter(value, unit, suffix);
    }

    if (nextFormatter && typeof nextFormatter === "function") {
      try {
        return nextFormatter(value, unit, suffix, epochMillis);
      } catch {
        // Fall back if nextFormatter throws
      }
    }

    return `${value} ${unit}${suffix}`;
  };

  if (Date.parse(time) + 30 * 24 * 60 * 60 * 1000 > new Date().getTime()) {
    // display "time ago" if less than 30 days ago
    return <TimeAgo date={time} formatter={customFormatter} />;
  }
  // display actual date otherwise
  return <>{isoToLocale(time)}</>;
}
