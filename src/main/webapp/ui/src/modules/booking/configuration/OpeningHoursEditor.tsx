import { type FormStore, useField } from "@formisch/react";
import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  ALL_ISO_WEEKDAYS,
  customOpeningHours,
  formatIsoWeekday,
  isOpenAllDay,
  type OpeningHoursEntry,
  type OpeningInterval,
  validOpeningHours,
} from "@/modules/booking/domain/bookingOpeningHours";
import { bookingDateTimeLocale, formatWallClockTime } from "@/modules/booking/domain/bookingTime";
import { RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME } from "@/modules/common/collection-form/responsiveFieldLayout";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { FieldDescription, FieldError } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";

const LABEL_CLASS_NAME = "text-sm font-medium @md:flex @md:min-h-9 @md:items-center @md:self-start";
const OPEN_ALL_DAY: OpeningInterval = { start: "00:00", end: "24:00" };
const FALLBACK_HOURS: OpeningInterval = { start: "09:00", end: "17:00" };
type Layout = "stacked" | "inline";

function SettingRow({ layout, label, children }: { layout: Layout; label: ReactNode; children: ReactNode }) {
  return (
    <div className={layout === "inline" ? RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME : "space-y-2"}>
      {label}
      <div className="flex min-h-9 min-w-0 flex-col justify-center gap-2">{children}</div>
    </div>
  );
}

function TimeRange({
  labelledBy,
  hours,
  remembered,
  onChange,
  onRemember,
  onTypedAllDay,
  disabled,
  invalid,
  error,
}: {
  labelledBy: string;
  hours: OpeningInterval;
  remembered: OpeningInterval | undefined;
  onChange: (hours: OpeningInterval) => void;
  onRemember: (hours: OpeningInterval) => void;
  onTypedAllDay: (selected: boolean) => void;
  disabled: boolean;
  invalid: boolean;
  error: ReactNode;
}) {
  const { t } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const id = useId();
  const allDay = isOpenAllDay(hours);
  const allDayRef = useRef<HTMLElement>(null);
  const focusAllDay = useRef(false);

  // Typing the whole day disables the edited input; loading and resetting must never move focus.
  useEffect(() => {
    if (focusAllDay.current && allDay) allDayRef.current?.focus();
    focusAllDay.current = false;
  }, [allDay]);

  const change = (next: OpeningInterval, typed: boolean) => {
    const custom = customOpeningHours(next) ?? customOpeningHours(hours);
    if (custom) onRemember(custom);
    const typedAllDay = typed && isOpenAllDay(next) && !allDay;
    focusAllDay.current = typedAllDay;
    onTypedAllDay(typedAllDay);
    onChange(next);
  };
  const midnightHelpId = `${id}-midnight`;
  const errorId = `${id}-error`;
  const input = (boundary: keyof OpeningInterval) => (
    <Input
      aria-label={t(boundary === "start" ? "settings.fields.openingStart" : "settings.fields.openingEnd")}
      type="time"
      step={60}
      required
      disabled={disabled || allDay}
      className="w-32"
      value={boundary === "end" && hours.end === "24:00" ? "00:00" : hours[boundary]}
      aria-invalid={invalid || undefined}
      aria-describedby={
        [boundary === "end" ? midnightHelpId : null, invalid ? errorId : null].filter(Boolean).join(" ") || undefined
      }
      onChange={(event) => {
        const value = event.currentTarget.value;
        change({ ...hours, [boundary]: boundary === "end" && value === "00:00" ? "24:00" : value }, true);
      }}
    />
  );
  return (
    <fieldset aria-labelledby={labelledBy} className="min-w-0 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <div className="flex min-w-0 max-w-full items-center gap-2">
          {input("start")}
          <span aria-hidden="true" className="shrink-0 text-muted-foreground">
            {"–"}
          </span>
          {input("end")}
        </div>
        <label className="flex items-center gap-2 text-sm whitespace-nowrap" htmlFor={`${id}-all-day`}>
          <Checkbox
            id={`${id}-all-day`}
            ref={allDayRef}
            checked={allDay}
            disabled={disabled}
            onCheckedChange={(checked) => change(checked ? OPEN_ALL_DAY : (remembered ?? FALLBACK_HOURS), false)}
          />
          {t("settings.openingHours.openAllDay")}
        </label>
      </div>
      <FieldDescription id={midnightHelpId}>
        {t("settings.openingHours.midnightEnd", {
          midnight: formatWallClockTime("00:00", bookingDateTimeLocale(timeFormat)),
        })}
      </FieldDescription>
      {invalid ? <FieldError id={errorId}>{error}</FieldError> : null}
    </fieldset>
  );
}

/** Seven independent weekday intervals. Closing a day retains its hours for this edit session. */
export function OpeningHoursEditor({ form, disabled, layout }: { form: FormStore; disabled: boolean; layout: Layout }) {
  const { t, i18n } = useTranslation("booking");
  const id = useId();
  const field = useField(form, { path: ["openingHours"] });
  const entries = Array.isArray(field.input) ? (field.input as OpeningHoursEntry[]) : [];
  const [closedDrafts, setClosedDrafts] = useState<Partial<Record<number, OpeningInterval>>>({});
  const [history, setHistory] = useState<Partial<Record<number, OpeningInterval>>>(() =>
    Object.fromEntries(entries.map((entry) => [entry.dayOfWeek, customOpeningHours(entry)])),
  );
  const [announcement, setAnnouncement] = useState("");
  const noDays = entries.length === 0;
  const changeDay = (dayOfWeek: number, hours: OpeningInterval | null) =>
    field.onChange(
      [
        ...entries.filter((entry) => entry.dayOfWeek !== dayOfWeek),
        ...(hours ? [{ dayOfWeek, ...hours }] : []),
      ].toSorted((left, right) => left.dayOfWeek - right.dayOfWeek),
    );
  return (
    <SettingRow
      layout={layout}
      label={
        <span id={`${id}-hours-label`} className={layout === "inline" ? LABEL_CLASS_NAME : "text-sm font-medium"}>
          {t("bookableItemDetails.fields.openingHours")}
        </span>
      }
    >
      <ul
        aria-labelledby={`${id}-hours-label`}
        aria-describedby={noDays ? `${id}-days-error` : undefined}
        className="@container divide-y rounded-sm border"
      >
        {ALL_ISO_WEEKDAYS.map((day) => {
          const entry = entries.find((candidate) => candidate.dayOfWeek === day);
          const dayName = formatIsoWeekday(day, "long", i18n.language === "cimode" ? "en-US" : i18n.language);
          return (
            <li key={day} className="flex flex-wrap items-start gap-x-4 gap-y-2 px-3 py-3">
              <div className="flex min-h-9 w-28 shrink-0 items-center gap-2">
                <Checkbox
                  id={`${id}-day-${day}`}
                  checked={Boolean(entry)}
                  disabled={disabled}
                  inputRef={day === 1 ? field.props.ref : undefined}
                  aria-invalid={noDays || undefined}
                  aria-describedby={noDays ? `${id}-days-error` : undefined}
                  onCheckedChange={(checked) => {
                    if (!checked && entry) setClosedDrafts((current) => ({ ...current, [day]: entry }));
                    changeDay(day, checked ? (closedDrafts[day] ?? OPEN_ALL_DAY) : null);
                  }}
                />
                <label id={`${id}-day-${day}-label`} htmlFor={`${id}-day-${day}`} className="text-sm font-medium">
                  {dayName}
                </label>
              </div>
              {entry ? (
                <div className="min-w-0 flex-1 basis-64">
                  <TimeRange
                    labelledBy={`${id}-day-${day}-label`}
                    hours={entry}
                    remembered={history[day]}
                    disabled={disabled}
                    invalid={!validOpeningHours(entry.start, entry.end)}
                    error={t("settings.errors.openingHours")}
                    onRemember={(hours) => setHistory((current) => ({ ...current, [day]: hours }))}
                    onTypedAllDay={(selected) =>
                      setAnnouncement(selected ? t("settings.openingHours.openAllDaySelected") : "")
                    }
                    onChange={(hours) => changeDay(day, hours)}
                  />
                </div>
              ) : (
                <span className="flex min-h-9 items-center text-sm text-muted-foreground">
                  {t("bookableItemDetails.openingHours.closed")}
                </span>
              )}
            </li>
          );
        })}
      </ul>
      {noDays ? <FieldError id={`${id}-days-error`}>{t("settings.openingHours.errors.noDays")}</FieldError> : null}
      <p aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </SettingRow>
  );
}
