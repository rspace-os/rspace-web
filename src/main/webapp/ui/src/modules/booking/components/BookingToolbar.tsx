import { CalendarDaysIcon, ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import * as React from "react";
import { TZDate } from "react-day-picker";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Calendar } from "@/modules/common/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/modules/common/ui/popover";
import { cn } from "@/modules/common/utils/cn";

export const bookingToolbarClassName =
  "grid grid-cols-1 gap-x-4 gap-y-3 border-b bg-muted/20 p-3 xl:flex xl:flex-nowrap xl:items-center";

function dateInTimeZone(date: string, timeZone: string): Date {
  const [year, month, day] = date.split("-").map(Number);
  return new TZDate(year, month - 1, day, timeZone);
}

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
  const [datePickerOpen, setDatePickerOpen] = React.useState(false);
  const selectedDate = dateInTimeZone(date, timeZone);

  return (
    <fieldset className={cn("flex min-w-0 items-center gap-2", className)}>
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
            <Button type="button" size="icon" variant="ghost" className="shrink-0" aria-label={jumpToDateLabel}>
              <CalendarDaysIcon aria-hidden="true" />
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
              onDateChange(todayInTimeZone(timeZone, selected));
              setDatePickerOpen(false);
            }}
          />
        </PopoverContent>
      </Popover>
    </fieldset>
  );
}

export function BookingTimeZoneBadge({ timeZone, label }: { timeZone: string; label: string }) {
  return (
    <Badge variant="outline" aria-label={label} className="h-9 shrink-0 rounded-sm bg-background px-2">
      {timeZone}
    </Badge>
  );
}
