import { Menu as MenuPrimitive } from "@base-ui/react/menu";
import { CalendarRangeIcon, CheckIcon, ChevronDownIcon, ListIcon, type LucideIcon, Rows3Icon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import { Button } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuSeparator, MenuTrigger } from "@/modules/common/ui/menu";
import {
  type CalendarLayout,
  type CalendarView,
  calendarLayouts,
  calendarViews,
  shiftDate,
} from "./calendarLayoutUtils";

const layoutIcons: Record<CalendarLayout, LucideIcon> = {
  "time-grid": CalendarRangeIcon,
  resources: Rows3Icon,
  agenda: ListIcon,
};

// Matches MenuItem in common/ui/menu.tsx, which wraps plain items only.
const menuItemClassName =
  "flex cursor-default items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none data-highlighted:bg-muted data-disabled:pointer-events-none data-disabled:opacity-50";
const menuGroupLabelClassName = "px-2 pt-1.5 pb-1 text-xs font-medium text-muted-foreground";

const isLayout = (value: unknown): value is CalendarLayout => calendarLayouts.some((layout) => layout === value);
const isView = (value: unknown): value is CalendarView => calendarViews.some((view) => view === value);

function RadioMenuItem({
  value,
  disabled,
  describedBy,
  children,
}: {
  value: string;
  disabled?: boolean;
  describedBy?: string;
  children: React.ReactNode;
}) {
  return (
    <MenuPrimitive.RadioItem
      value={value}
      disabled={disabled}
      aria-describedby={describedBy}
      className={menuItemClassName}
    >
      <span className="flex size-4 shrink-0 items-center justify-center">
        <MenuPrimitive.RadioItemIndicator>
          <CheckIcon aria-hidden="true" className="size-4" />
        </MenuPrimitive.RadioItemIndicator>
      </span>
      {children}
    </MenuPrimitive.RadioItem>
  );
}

/** One menu for the calendar's layout and period; it stays open so both can be chosen in turn. */
function CalendarViewMenu({
  view,
  layout,
  onViewChange,
  onLayoutChange,
}: {
  view: CalendarView;
  layout: CalendarLayout;
  onViewChange: (view: CalendarView) => void;
  onLayoutChange: (layout: CalendarLayout) => void;
}) {
  const { t } = useTranslation("booking");
  const monthReasonId = React.useId();
  const LayoutIcon = layoutIcons[layout];
  const monthUnavailable = layout === "resources";
  const summary = t("calendar.view.summary", {
    layout: t(`calendar.layout.${layout}`),
    period: t(`calendar.period.${view}`),
  });
  return (
    <Menu>
      {/* The name keeps the visible summary, so speech input can still target it by what it shows. */}
      <MenuTrigger
        render={<Button type="button" variant="outline" aria-label={t("calendar.view.trigger", { summary })} />}
      >
        <LayoutIcon aria-hidden="true" data-icon="inline-start" />
        {summary}
        <ChevronDownIcon aria-hidden="true" data-icon="inline-end" />
      </MenuTrigger>
      <MenuContent className="w-64">
        <MenuPrimitive.RadioGroup
          value={layout}
          onValueChange={(value) => {
            if (!isLayout(value)) return;
            if (value === "resources" && view === "month") onViewChange("week");
            onLayoutChange(value);
          }}
        >
          <MenuPrimitive.GroupLabel className={menuGroupLabelClassName}>
            {t("calendar.layout.legend")}
          </MenuPrimitive.GroupLabel>
          {calendarLayouts.map((option) => {
            const Icon = layoutIcons[option];
            return (
              <RadioMenuItem key={option} value={option}>
                <Icon aria-hidden="true" className="size-4 text-muted-foreground" />
                {t(`calendar.layout.${option}`)}
              </RadioMenuItem>
            );
          })}
        </MenuPrimitive.RadioGroup>
        <MenuSeparator />
        <MenuPrimitive.RadioGroup value={view} onValueChange={(value) => isView(value) && onViewChange(value)}>
          <MenuPrimitive.GroupLabel className={menuGroupLabelClassName}>
            {t("calendar.period.legend")}
          </MenuPrimitive.GroupLabel>
          {calendarViews.map((option) => {
            const disabled = monthUnavailable && option === "month";
            return (
              <RadioMenuItem
                key={option}
                value={option}
                disabled={disabled}
                describedBy={disabled ? monthReasonId : undefined}
              >
                <span className="min-w-0">
                  <span className="block">{t(`calendar.period.${option}`)}</span>
                  {disabled ? (
                    <span aria-hidden="true" className="block text-xs text-muted-foreground">
                      {t("calendar.period.monthUnavailableShort")}
                    </span>
                  ) : null}
                </span>
              </RadioMenuItem>
            );
          })}
        </MenuPrimitive.RadioGroup>
        {monthUnavailable ? (
          <span id={monthReasonId} className="sr-only">
            {t("calendar.period.monthUnavailableInResources")}
          </span>
        ) : null}
      </MenuContent>
    </Menu>
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
      <div className="ml-auto">
        <CalendarViewMenu view={view} layout={layout} onViewChange={onViewChange} onLayoutChange={onLayoutChange} />
      </div>
    </>
  );
}
