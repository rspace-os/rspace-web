import { useTranslation } from "react-i18next";
import TimeAgo from "react-timeago-i18n";
import { isoToLocale } from "@/util/Util";

const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;
const COMPACT_FORMAT_OPTIONS: Intl.RelativeTimeFormatOptions = {
  numeric: "always",
  style: "narrow",
};

type TimeAgoCustomProps = {
  time: string;
  compact?: boolean;
};

const TimeAgoCustom = ({ time, compact = false }: TimeAgoCustomProps) => {
  const { t } = useTranslation();
  const timestamp = Date.parse(time);
  const shouldRenderRelativeTime = timestamp + THIRTY_DAYS_MS > Date.now();
  const hideSecondsText: [string, string] = [t("timeAgo.lessThanOneMinuteAgo"), t("timeAgo.inLessThanOneMinute")];

  if (shouldRenderRelativeTime) {
    return (
      <TimeAgo
        date={timestamp}
        locale="en-US"
        hideSeconds
        hideSecondsText={hideSecondsText}
        formatOptions={compact ? COMPACT_FORMAT_OPTIONS : undefined}
        roundStrategy={compact ? "floor" : undefined}
        allowFuture={compact}
      />
    );
  }
  return <>{isoToLocale(time)}</>;
};

export default TimeAgoCustom;
