import type { ReactElement, ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { bookingHourCycle } from "@/modules/booking/domain/bookingTime";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";

export type BookingInstrumentTimeTooltipProps = {
  start?: string;
  end?: string;
  /** Already formatted instrument-local text, for values that are not instants, such as weekly opening hours. */
  time?: string;
  displayTimeZone: string;
  instrumentTimeZone: string | null | undefined;
  trigger?: ReactElement;
  children?: ReactNode;
};

export function BookingInstrumentTimeTooltip({
  start,
  end,
  time,
  displayTimeZone,
  instrumentTimeZone,
  trigger,
  children,
}: BookingInstrumentTimeTooltipProps) {
  const { t, i18n } = useTranslation("booking");
  const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  const showTooltip =
    (!!start || !!time) &&
    !!instrumentTimeZone &&
    (instrumentTimeZone !== displayTimeZone || instrumentTimeZone !== browserTimeZone);

  if (!showTooltip && !trigger) return children ?? null;

  const formatter = new Intl.DateTimeFormat(i18n.language, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hourCycle: bookingHourCycle(),
    timeZoneName: "shortOffset",
    timeZone: instrumentTimeZone ?? "UTC",
  });
  const dateTime =
    time ??
    (start && end
      ? `${formatter.format(new Date(start))} – ${formatter.format(new Date(end))}`
      : start
        ? formatter.format(new Date(start))
        : "");
  const tooltipTrigger = trigger ?? (
    <button
      type="button"
      className="inline cursor-help appearance-none rounded-sm border-0 bg-transparent p-0 text-inherit focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring"
    />
  );

  return (
    <Tooltip disabled={!showTooltip}>
      {children === undefined ? (
        <TooltipTrigger render={tooltipTrigger} />
      ) : (
        <TooltipTrigger render={tooltipTrigger}>{children}</TooltipTrigger>
      )}
      {showTooltip && (
        <TooltipContent role="tooltip">
          {t("bookings.instrumentTimeTooltip", {
            dateTime,
            timezone: instrumentTimeZone,
          })}
        </TooltipContent>
      )}
    </Tooltip>
  );
}
