import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
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
  disabledReason,
  optionLabel,
  onChange,
}: {
  legend: string;
  options: readonly Option[];
  value: Option;
  isDisabled?: (option: Option) => boolean;
  /** Why a disabled option is unavailable; shown as a tooltip and exposed as its accessible description. */
  disabledReason?: (option: Option) => string | undefined;
  optionLabel: (option: Option) => string;
  onChange: (option: Option) => void;
}) {
  const reasonIdPrefix = React.useId();
  return (
    <ButtonGroup aria-label={legend}>
      {options.map((option, index) => {
        const disabled = isDisabled?.(option) ?? false;
        const reason = disabled ? disabledReason?.(option) : undefined;
        const reasonId = `${reasonIdPrefix}-${option}-reason`;
        const button = (
          <Button
            key={option}
            type="button"
            variant={value === option ? "secondary" : "outline"}
            aria-pressed={value === option}
            aria-describedby={reason ? reasonId : undefined}
            disabled={disabled}
            // The tooltip wrapper hides this button from the group's child selectors, so repeat their joins.
            className={cn(
              reason && index > 0 && "rounded-l-none border-l-0",
              reason && index < options.length - 1 && "rounded-r-none",
            )}
            onClick={() => onChange(option)}
          >
            {optionLabel(option)}
          </Button>
        );
        if (!reason) return button;
        return (
          <Tooltip key={option}>
            {/* A disabled button receives no pointer events, so the wrapper opens the tooltip. */}
            <TooltipTrigger render={<span className="inline-flex" />}>
              {button}
              <span id={reasonId} className="sr-only">
                {reason}
              </span>
            </TooltipTrigger>
            <TooltipContent side="bottom" className="rounded-sm">
              {reason}
            </TooltipContent>
          </Tooltip>
        );
      })}
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
            disabledReason={() => t("calendar.period.monthUnavailableInResources")}
            optionLabel={(option) => t(`calendar.period.${option}`)}
            onChange={onViewChange}
          />
        </div>
      </fieldset>
    </>
  );
}
