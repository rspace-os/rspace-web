import TimeAgo from "react-timeago";
import { isoToLocale } from "@/util/Util";
import { Formatter } from "react-timeago";
import { makeIntlFormatter } from "react-timeago/defaultFormatter";

type UserDetailsArgs = {
  time: string;
  formatter?: Formatter;
};

const intlFormatter = makeIntlFormatter({
  locale: 'en-US',
  localeMatcher: 'best fit',
  numberingSystem: 'latn',
  style: 'long',
  numeric: 'auto',
});

const TimeAgoCustom = ({ time, formatter }: UserDetailsArgs) => {
  const customFormatter: Formatter = (
    value,
    unit,
    suffix,
    epochMilliseconds,
    nextFormatter,
    now,
  ) => {
    if (unit === "second") {
      return "< 1 minute ago";
    }

    if (typeof formatter === "function") {
      // Not sure what's causing this
      // eslint-disable-next-line @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-return
      return formatter(
        value,
        unit,
        suffix,
        epochMilliseconds,
        nextFormatter,
        now,
      );
    }

    if (typeof nextFormatter === "function") {
      try {
        // Not sure what's causing this
        // eslint-disable-next-line @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-return
        return nextFormatter(
          value,
          unit,
          suffix,
          epochMilliseconds,
          intlFormatter,
          now,
        );
      } catch {
        return `${value} ${unit}${suffix}`;
      }
    }

    return `${value} ${unit}${suffix}`;
  };

  // display "time ago" if less than 30 days ago
  if (Date.parse(time) + 30 * 24 * 60 * 60 * 1000 > new Date().getTime()) {
    // Not sure what's causing this
    // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
    return <TimeAgo date={time} formatter={customFormatter} />;
  }
  // display actual date otherwise
  return <>{isoToLocale(time)}</>;
}

export default TimeAgoCustom;