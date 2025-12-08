import type { Formatter } from "react-timeago";
import TimeAgo from "react-timeago";
import { makeIntlFormatter } from "react-timeago/defaultFormatter";
import { isoToLocale } from "@/util/Util";

type UserDetailsArgs = {
    time: string;
    formatter?: Formatter;
};

const intlFormatter = makeIntlFormatter({
    locale: "en-US",
    localeMatcher: "best fit",
    numberingSystem: "latn",
    style: "long",
    numeric: "auto",
});

const TimeAgoCustom = ({ time, formatter }: UserDetailsArgs) => {
    const customFormatter: Formatter = (value, unit, suffix, epochMilliseconds, nextFormatter, now) => {
        if (unit === "second") {
            return "< 1 minute ago";
        }

        if (typeof formatter === "function") {
            return formatter(value, unit, suffix, epochMilliseconds, nextFormatter, now);
        }

        if (typeof nextFormatter === "function") {
            try {
                return nextFormatter(value, unit, suffix, epochMilliseconds, intlFormatter, now);
            } catch {
                return `${value} ${unit}${suffix}`;
            }
        }

        return `${value} ${unit}${suffix}`;
    };

    // display "time ago" if less than 30 days ago
    if (Date.parse(time) + 30 * 24 * 60 * 60 * 1000 > Date.now()) {
        // Not sure what's causing this
        return <TimeAgo date={time} formatter={customFormatter} />;
    }
    // display actual date otherwise
    return <>{isoToLocale(time)}</>;
};

export default TimeAgoCustom;
