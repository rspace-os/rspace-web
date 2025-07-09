import React from "react";
import TimeAgo from "react-timeago";
import { isoToLocale } from "../util/Util";
import { Formatter, Suffix, Unit } from "react-timeago/es6/types";

type UserDetailsArgs = {
  time: string;
  formatter?: (
    value: number,
    unit: Unit,
    suffix: Suffix,
    epochMilliseconds: number,
    nextFormatter?: Formatter,
    now?: () => number,
  ) => React.ReactNode;
};

export default function UserDetails({
  time,
  formatter,
}: UserDetailsArgs): JSX.Element {
  const customFormatter = (
    value: number,
    unit: Unit,
    suffix: Suffix,
    epochMilliseconds: number,
    nextFormatter: Formatter,
    now: () => number,
  ): React.ReactNode => {
    if (unit === "second") {
      return "< 1 minute ago";
    }

    if (formatter) {
      return formatter(
        value,
        unit,
        suffix,
        epochMilliseconds,
        nextFormatter,
        now,
      );
    }

    if (nextFormatter && typeof nextFormatter === "function") {
      try {
        return nextFormatter(
          value,
          unit,
          suffix,
          epochMilliseconds,
          nextFormatter,
          now,
        );
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
