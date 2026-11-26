import { Temporal } from "@js-temporal/polyfill";
import { CalendarDaysIcon, ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { pickerDate, plainDateFromPicker } from "@/modules/booking/bookingDates";
import { bookingDisplayLocale } from "@/modules/booking/domain/bookingTime";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Calendar } from "@/modules/common/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/modules/common/ui/popover";
import { cn } from "@/modules/common/utils/cn";

export function BookingDateControls({
  date,
  today,
  timeZone,
  controlsLabel,
  navigationLabel,
  previousLabel,
  todayLabel,
  nextLabel,
  jumpToDateLabel,
  onPrevious,
  onNext,
  onDateChange,
  className,
}: {
  date: string;
  today: string;
  timeZone: string;
  controlsLabel: string;
  navigationLabel: string;
  previousLabel: string;
  todayLabel: string;
  nextLabel: string;
  jumpToDateLabel: string;
  onPrevious: () => void;
  onNext: () => void;
  onDateChange: (date: string) => void;
  className?: string;
}) {
  const { t } = useTranslation("booking");
  const [datePickerOpen, setDatePickerOpen] = React.useState(false);
  const selectedPlainDate = Temporal.PlainDate.from(date);
  const selectedDate = pickerDate(selectedPlainDate, timeZone);
  const selectedDateLabel = new Intl.DateTimeFormat(bookingDisplayLocale(), { dateStyle: "medium", timeZone }).format(
    selectedPlainDate.toZonedDateTime(timeZone).epochMilliseconds,
  );

  return (
    <fieldset className={cn("flex min-w-0 flex-wrap items-center gap-2", className)}>
      <legend className="sr-only">{controlsLabel}</legend>
      <ButtonGroup aria-label={navigationLabel} className="shrink-0">
        <Button type="button" size="icon" variant="outline" aria-label={previousLabel} onClick={onPrevious}>
          <ChevronLeftIcon aria-hidden="true" />
        </Button>
        <Button type="button" variant="outline" onClick={() => onDateChange(today)}>
          {todayLabel}
        </Button>
        <Button type="button" size="icon" variant="outline" aria-label={nextLabel} onClick={onNext}>
          <ChevronRightIcon aria-hidden="true" />
        </Button>
      </ButtonGroup>
      <Popover open={datePickerOpen} onOpenChange={setDatePickerOpen}>
        <PopoverTrigger
          render={
            <Button
              type="button"
              variant="ghost"
              className="shrink-0"
              aria-label={t("toolbar.jumpToDateAccessible", {
                label: jumpToDateLabel,
                date: selectedDateLabel,
              })}
            >
              <CalendarDaysIcon aria-hidden="true" />
              <time dateTime={date}>{selectedDateLabel}</time>
            </Button>
          }
        />
        <PopoverContent className="w-auto p-0">
          <Calendar
            mode="single"
            selected={selectedDate}
            defaultMonth={selectedDate}
            timeZone={timeZone}
            noonSafe
            onSelect={(selected) => {
              if (!selected) return;
              onDateChange(plainDateFromPicker(selected, timeZone).toString());
              setDatePickerOpen(false);
            }}
          />
        </PopoverContent>
      </Popover>
    </fieldset>
  );
}
