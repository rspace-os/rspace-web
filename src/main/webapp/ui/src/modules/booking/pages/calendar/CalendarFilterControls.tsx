import { useTranslation } from "react-i18next";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import {
  type CalendarLayout,
  type CalendarView,
  calendarLayouts,
  calendarViews,
  shiftDate,
} from "./calendarLayoutUtils";

function SegmentedControl<Option extends string>({
  legend,
  options,
  value,
  isDisabled,
  optionLabel,
  onChange,
}: {
  legend: string;
  options: readonly Option[];
  value: Option;
  isDisabled?: (option: Option) => boolean;
  optionLabel: (option: Option) => string;
  onChange: (option: Option) => void;
}) {
  return (
    <ButtonGroup aria-label={legend}>
      {options.map((option) => (
        <Button
          key={option}
          type="button"
          variant={value === option ? "secondary" : "outline"}
          aria-pressed={value === option}
          disabled={isDisabled?.(option)}
          onClick={() => onChange(option)}
        >
          {optionLabel(option)}
        </Button>
      ))}
    </ButtonGroup>
  );
}

export function CalendarFilterControls({
  date,
  view,
  layout,
  timezone,
  today,
  onDateChange,
  onViewChange,
  onLayoutChange,
}: {
  date: string;
  view: CalendarView;
  layout: CalendarLayout;
  timezone: string;
  today: string;
  onDateChange: (date: string) => void;
  onViewChange: (view: CalendarView) => void;
  onLayoutChange: (layout: CalendarLayout) => void;
}) {
  const { t } = useTranslation("booking");
  const periodLabel = t(`calendar.period.${view}`).toLocaleLowerCase();
  return (
    <>
      <BookingDateControls
        date={date}
        today={today}
        timeZone={timezone}
        controlsLabel={t("calendar.dateControls")}
        navigationLabel={t("calendar.periodNavigation")}
        previousLabel={t("calendar.previousPeriod", { period: periodLabel })}
        todayLabel={t("calendar.today")}
        nextLabel={t("calendar.nextPeriod", { period: periodLabel })}
        jumpToDateLabel={t("calendar.jumpToDate")}
        onPrevious={() => onDateChange(shiftDate(date, view, -1))}
        onNext={() => onDateChange(shiftDate(date, view, 1))}
        onDateChange={onDateChange}
      />
      <fieldset className="ml-auto min-w-0">
        <legend className="sr-only">{t("calendar.displayControls")}</legend>
        <div className="flex flex-wrap items-center gap-2">
          <SegmentedControl
            legend={t("calendar.layout.legend")}
            options={calendarLayouts}
            value={layout}
            optionLabel={(option) => t(`calendar.layout.${option}`)}
            onChange={(option) => {
              if (option === "resources" && view === "month") onViewChange("week");
              onLayoutChange(option);
            }}
          />
          <SegmentedControl
            legend={t("calendar.period.legend")}
            options={calendarViews}
            value={view}
            isDisabled={(option) => layout === "resources" && option === "month"}
            optionLabel={(option) => t(`calendar.period.${option}`)}
            onChange={onViewChange}
          />
        </div>
      </fieldset>
    </>
  );
}
