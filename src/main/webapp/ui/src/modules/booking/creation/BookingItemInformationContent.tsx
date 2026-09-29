import { GlobeIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { type WeekdayOpening, weeklyOpeningHours } from "@/modules/booking/configuration/openingHoursFacts";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatIsoWeekday } from "@/modules/booking/domain/bookingOpeningHours";
import {
  bookingDateTimeLocale,
  currentWallClock,
  formatDurationMinutes,
  isPlainDate,
  sameTimeZone,
} from "@/modules/booking/domain/bookingTime";
import { CardContent } from "@/modules/common/ui/card";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Separator } from "@/modules/common/ui/separator";

/**
 * A line such as "Monday: 5:00 PM (+1) - 11:00 PM (+1)" with no-break spaces inside each time and before each dash, so
 * it wraps only after the day and after a dash, never stranding "PM", a day offset or a dash at the start of a line.
 */
function keepTimesTogether(line: string): string {
  return line.replace(/(?<=\d) | (?=\([+-]\d+\)|- )/g, "\u00a0");
}

/**
 * A time that shows its value in the item's own timezone in the shared instrument-time tooltip. The trigger is an
 * unstyled button around the whole time and its globe, so it stays keyboard-reachable; its name is the visible time
 * followed by the tooltip text, which is otherwise only shown on hover or focus.
 */
function ItemTimezoneTime({
  time,
  itemTime,
  timezone,
  displayTimezone,
}: {
  time: string;
  itemTime: string;
  timezone: string;
  displayTimezone: string;
}) {
  const { t } = useTranslation("booking");
  return (
    <BookingInstrumentTimeTooltip
      time={itemTime}
      displayTimeZone={displayTimezone}
      instrumentTimeZone={timezone}
      trigger={
        <button
          type="button"
          aria-label={`${time}, ${t("bookings.instrumentTimeTooltip", { dateTime: itemTime, timezone })}`}
          className="inline-flex cursor-help appearance-none items-center gap-1 rounded-sm border-0 bg-transparent p-0 text-left text-inherit focus-visible:outline-2 focus-visible:outline-ring"
        />
      }
    >
      <span>{keepTimesTogether(time)}</span>
      <GlobeIcon aria-hidden="true" className="size-3.5 shrink-0" />
    </BookingInstrumentTimeTooltip>
  );
}

/**
 * "Every day: …" when every weekday reads the same, otherwise one line per weekday. With `itemDays` (the same week in
 * the item's timezone), each time shows its value in that timezone on hover; the line already names the day, so the
 * tooltip gives only the hours unless the item's days differ from each other.
 */
function WeeklyOpeningHours({
  days,
  displayTimezone,
  itemDays,
}: {
  days: WeekdayOpening[];
  displayTimezone: string;
  itemDays?: { timezone: string; days: WeekdayOpening[] };
}) {
  const { t } = useTranslation("booking");
  const everyDay = (week: WeekdayOpening[]) => {
    const [first] = week;
    return first?.hours && week.every((day) => day.hours === first.hours) ? first.hours : null;
  };
  const dayLine = (dayOfWeek: number, hours: string | null) => {
    const day = formatIsoWeekday(dayOfWeek);
    return hours === null
      ? t("bookings.itemInformation.closedDay", { day })
      : `${t("bookings.itemInformation.day", { day })} ${hours}`;
  };
  const line = (key: number, time: string, itemTime: string | null) => (
    <li key={key} className="flex items-center gap-1">
      {itemDays && itemTime ? (
        <ItemTimezoneTime
          time={time}
          itemTime={itemTime}
          timezone={itemDays.timezone}
          displayTimezone={displayTimezone}
        />
      ) : (
        keepTimesTogether(time)
      )}
    </li>
  );
  // The list keeps to the right of the value column, but its lines start together so the days line up.
  const listClassName = "ml-auto w-fit text-left";

  const hours = everyDay(days);
  if (hours) {
    const itemHours = itemDays && everyDay(itemDays.days);
    return (
      <ul className={listClassName}>
        {line(
          0,
          t("bookings.itemInformation.everyDay", { hours }),
          itemDays
            ? itemHours
              ? itemHours
              : itemDays.days.map(({ dayOfWeek, hours: dayHours }) => dayLine(dayOfWeek, dayHours)).join("; ")
            : null,
        )}
      </ul>
    );
  }
  return (
    <ul className={listClassName}>
      {days.map(({ dayOfWeek, hours: dayHours }) => {
        const itemHours = itemDays?.days.find((candidate) => candidate.dayOfWeek === dayOfWeek)?.hours ?? null;
        return line(
          dayOfWeek,
          dayLine(dayOfWeek, dayHours),
          dayHours === null || itemHours === null ? null : itemHours,
        );
      })}
    </ul>
  );
}

/**
 * Opening hours are shown in the viewer's preferred timezone for the week of `date` (today when none is chosen),
 * since daylight saving changes the conversion; each time's value in the item's own timezone is behind a globe button.
 */
export function BookingItemInformationContent({
  item,
  displayTimezone,
  date,
}: {
  item: BookableItemOption;
  displayTimezone: string;
  date?: string;
}) {
  const { t } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const referenceDate =
    date && isPlainDate(date) ? date : currentWallClock(new Date().toISOString(), displayTimezone).date;
  const facts: Array<[string, ReactNode]> = [
    [
      t("bookings.itemInformation.open"),
      <WeeklyOpeningHours
        key="open"
        days={weeklyOpeningHours(item, displayTimezone, referenceDate, bookingDateTimeLocale(timeFormat))}
        displayTimezone={displayTimezone}
        itemDays={
          sameTimeZone(item.timezone, displayTimezone)
            ? undefined
            : {
                timezone: item.timezone,
                days: weeklyOpeningHours(item, item.timezone, referenceDate, bookingDateTimeLocale(timeFormat)),
              }
        }
      />,
    ],
    [
      t("bookableItemDetails.fields.granularity"),
      t("bookableItemDetails.minutes", { count: item.slotGranularityMinutes }),
    ],
    [
      t("bookableItemDetails.fields.maximumDuration"),
      item.maxBookingDurationMinutes === 0
        ? t("bookableItemDetails.unlimited")
        : formatDurationMinutes(item.maxBookingDurationMinutes),
    ],
    [
      t("bookings.itemInformation.doubleBookingAllowed"),
      item.allowDoubleBooking ? t("bookableItemDetails.yes") : t("bookableItemDetails.no"),
    ],
  ];
  const buffer: string[] = [];
  if (item.bufferBeforeMinutes > 0) {
    buffer.push(t("bookings.itemInformation.bufferBefore", { count: item.bufferBeforeMinutes }));
  }
  if (item.bufferAfterMinutes > 0) {
    buffer.push(t("bookings.itemInformation.bufferAfter", { count: item.bufferAfterMinutes }));
  }
  if (buffer.length > 0) facts.push([t("bookings.itemInformation.buffer"), buffer.join(", ")]);

  return (
    <>
      <CardContent className="py-2">
        <InventoryItem
          name={item.name}
          globalId={item.globalId}
          href={`/globalId/${item.globalId}`}
          idLinkLabel={t("bookings.form.openItem", { globalId: item.globalId })}
          size="sm"
          className="border-0 p-0"
        />
      </CardContent>
      <Separator className="h-px" />
      <CardContent className="py-4">
        <dl className="grid grid-cols-2 items-baseline gap-x-4 gap-y-2 text-sm">
          {facts.map(([label, value]) => (
            <div className="contents" key={label}>
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="min-w-0 break-words text-right font-medium">{value}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </>
  );
}
