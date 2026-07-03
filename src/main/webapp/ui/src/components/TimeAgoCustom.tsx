import TimeAgo from "react-timeago-i18n";
import { isoToLocale } from "@/util/Util";

const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;
const HIDE_SECONDS_TEXT: [string, string] = ["< 1 minute ago", "in < 1 minute"];
const COMPACT_FORMAT_OPTIONS: Intl.RelativeTimeFormatOptions = {
  numeric: "always",
  style: "narrow",
};

type TimeAgoCustomProps = {
  time: string;
  compact?: boolean;
};

const TimeAgoCustom = ({ time, compact = false }: TimeAgoCustomProps) => {
  const timestamp = Date.parse(time);
  const shouldRenderRelativeTime = timestamp + THIRTY_DAYS_MS > Date.now();

  if (shouldRenderRelativeTime) {
    return (
      <TimeAgo
        date={timestamp}
        locale="en-US"
        hideSecondsText={HIDE_SECONDS_TEXT}
        formatOptions={compact ? COMPACT_FORMAT_OPTIONS : undefined}
        roundStrategy={compact ? "floor" : undefined}
        allowFuture={compact}
      />
    );
  }
  return <>{isoToLocale(time)}</>;
};

export default TimeAgoCustom;
