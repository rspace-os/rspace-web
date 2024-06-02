//@flow

import React, { type Node } from "react";
import TimeAgo from "react-timeago";
import { isoToLocale } from "../util/Util";

type UserDetailsArgs = {|
  time: string,
  formatter?: (number, string, string) => string,
|};

export default function UserDetails({
  time,
  formatter,
}: UserDetailsArgs): Node {
  const f = (
    value: number,
    unit: string,
    suffix: string,
    _: mixed,
    nextFormatter: (number, string, string) => string
  ) =>
    unit === "second"
      ? "< 1 minute ago"
      : (formatter ?? nextFormatter)(value, unit, suffix);
  if (Date.parse(time) + 30 * 24 * 60 * 60 * 1000 > new Date().getTime()) {
    // display "time ago" if less than 30 days ago
    return <TimeAgo date={time} formatter={f} />;
  } else {
    // display actual date otherwise
    return isoToLocale(time);
  }
}
