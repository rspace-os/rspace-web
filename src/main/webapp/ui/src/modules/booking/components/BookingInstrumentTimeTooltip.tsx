import type { ReactElement, ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { browserTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";

export type BookingInstrumentTimeTooltipProps = {
  start?: string;
  end?: string;
  displayTimeZone: string;
  instrumentTimeZone: string | null | undefined;
  trigger?: ReactElement;
  children?: ReactNode;
};

export function BookingInstrumentTimeTooltip({
  start,
  end,
  displayTimeZone,
  instrumentTimeZone,
  trigger,
  children,
}: BookingInstrumentTimeTooltipProps) {
  const { t, i18n } = useTranslation("booking");
  const browserZone = browserTimeZone();

  const showTooltip =
    !!start &&
    !!instrumentTimeZone &&
    (instrumentTimeZone !== displayTimeZone || (browserZone !== null && instrumentTimeZone !== browserZone));

  if (!showTooltip && !trigger) return children ?? null;

  const formatter = new Intl.DateTimeFormat(i18n.language, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZoneName: "shortOffset",
    timeZone: instrumentTimeZone ?? "UTC",
  });
  const dateTime =
    start && end
      ? `${formatter.format(new Date(start))} – ${formatter.format(new Date(end))}`
      : start
        ? formatter.format(new Date(start))
        : "";
  const tooltipTrigger = trigger ?? (
    <button
      type="button"
      className="inline cursor-help appearance-none rounded-sm border-0 bg-transparent p-0 text-inherit focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring"
    />
  );

  return (
    <Tooltip disabled={!showTooltip}>
      <TooltipTrigger render={tooltipTrigger}>{children}</TooltipTrigger>
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
