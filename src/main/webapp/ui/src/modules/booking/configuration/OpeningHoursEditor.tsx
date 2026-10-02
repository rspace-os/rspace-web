import { type FormStore, useField } from "@formisch/react";
import { CheckIcon, PencilIcon, Undo2Icon, XIcon } from "lucide-react";
import { type ReactNode, type Ref, useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  ALL_ISO_WEEKDAYS,
  formatIsoWeekday,
  formatOpeningRange,
  type OpeningException,
  type OpeningHours,
  validOpeningHours,
} from "@/modules/booking/domain/bookingOpeningHours";
import { formatWallClockTime } from "@/modules/booking/domain/bookingTime";
import { RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME } from "@/modules/common/collection-form/responsiveFieldLayout";
import { Button } from "@/modules/common/ui/button";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { FieldDescription, FieldError } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { cn } from "@/modules/common/utils/cn";

// Mirrors FormField's inline row: the label keeps a 36px line at the top, and the control column is at
// least one 36px line, so single-line controls centre against the label and taller ones grow downward.
const LABEL_CLASS_NAME = "text-sm font-medium @md:flex @md:min-h-9 @md:items-center @md:self-start";

type Layout = "stacked" | "inline";

function SettingRow({ layout, label, children }: { layout: Layout; label: ReactNode; children: ReactNode }) {
  return (
    <div className={layout === "inline" ? RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME : "space-y-2"}>
      {label}
      <div className="flex min-h-9 min-w-0 flex-col justify-center gap-2">{children}</div>
    </div>
  );
}

/** One 36px line, so a day switching between its read-out and its inputs keeps its row height. */
function TimeRange({
  labelledBy,
  hours,
  onChange,
  disabled,
  invalid,
  error,
  inputRefs,
}: {
  labelledBy: string;
  hours: OpeningHours;
  onChange: (hours: OpeningHours) => void;
  disabled: boolean;
  invalid: boolean;
  error: ReactNode;
  inputRefs?: { start?: Ref<HTMLInputElement>; end?: Ref<HTMLInputElement> };
}) {
  const { t } = useTranslation("booking");
  const id = useId();
  const input = (boundary: keyof OpeningHours) => (
    <Input
      aria-label={t(boundary === "start" ? "settings.fields.openingStart" : "settings.fields.openingEnd")}
      type="time"
      step={60}
      required
      disabled={disabled}
      ref={inputRefs?.[boundary]}
      value={boundary === "end" && hours.end === "24:00" ? "00:00" : hours[boundary]}
      aria-invalid={invalid || undefined}
      aria-describedby={invalid ? `${id}-error` : undefined}
      onChange={(event) => {
        const value = event.currentTarget.value;
        onChange({ ...hours, [boundary]: boundary === "end" && value === "00:00" ? "24:00" : value });
      }}
    />
  );
  return (
    <fieldset aria-labelledby={labelledBy} className="min-w-0 space-y-2">
      <div className="grid max-w-xs grid-cols-[minmax(5.5rem,1fr)_auto_minmax(5.5rem,1fr)] items-center gap-2">
        {input("start")}
        <span aria-hidden="true" className="text-muted-foreground">
          {"–"}
        </span>
        {input("end")}
      </div>
      {invalid ? <FieldError id={`${id}-error`}>{error}</FieldError> : null}
    </fieldset>
  );
}

/**
 * The weekly opening-hours block: open weekdays, the shared hours, and per-day exceptions. Unconfirmed day edits
 * are local drafts, never part of the form value; `onSaveBlockedChange` reports when one is pending on an open day,
 * or when no day is open, so the owning form can disable saving.
 */
export function OpeningHoursEditor({
  form,
  disabled,
  layout,
  onSaveBlockedChange,
}: {
  form: FormStore;
  disabled: boolean;
  layout: Layout;
  onSaveBlockedChange?: (blocked: boolean) => void;
}) {
  const { t } = useTranslation("booking");
  const id = useId();
  const openDaysField = useField(form, { path: ["openDays"] });
  const exceptionsField = useField(form, { path: ["openingExceptions"] });
  const openingStart = useField(form, { path: ["openingStart"] });
  const openingEnd = useField(form, { path: ["openingEnd"] });
  const openDays = Array.isArray(openDaysField.input) ? (openDaysField.input as number[]) : [];
  const exceptions = Array.isArray(exceptionsField.input) ? (exceptionsField.input as OpeningException[]) : [];
  const shared = {
    start: typeof openingStart.input === "string" ? openingStart.input : "",
    end: typeof openingEnd.input === "string" ? openingEnd.input : "",
  };
  const [drafts, setDrafts] = useState<Partial<Record<number, OpeningHours>>>({});
  const [listOpen, setListOpen] = useState(() => exceptions.length > 0);
  const selectedDays = ALL_ISO_WEEKDAYS.filter((day) => openDays.includes(day));
  const noDays = selectedDays.length === 0;
  const pendingDraft = selectedDays.some((day) => drafts[day]);
  const showList = listOpen || exceptions.some((exception) => openDays.includes(exception.dayOfWeek));
  const sharedInvalid =
    !validOpeningHours(shared.start, shared.end) ||
    Boolean(openingStart.errors?.length) ||
    Boolean(openingEnd.errors?.length);
  const blocked = noDays || pendingDraft;

  useEffect(() => {
    onSaveBlockedChange?.(blocked);
  }, [blocked, onSaveBlockedChange]);
  useEffect(() => () => onSaveBlockedChange?.(false), [onSaveBlockedChange]);

  const withoutDraft = (day: number) =>
    setDrafts((current) => Object.fromEntries(Object.entries(current).filter(([key]) => Number(key) !== day)));
  const setExceptions = (next: readonly OpeningException[]) =>
    exceptionsField.onChange(next.toSorted((left, right) => left.dayOfWeek - right.dayOfWeek));
  const iconButton = (label: string, icon: ReactNode, onClick: () => void, buttonDisabled = false) => (
    <Button
      type="button"
      variant="ghost"
      size="icon-sm"
      aria-label={label}
      title={label}
      disabled={disabled || buttonDisabled}
      onClick={onClick}
    >
      {icon}
    </Button>
  );
  const labelClassName = layout === "inline" ? LABEL_CLASS_NAME : "text-sm font-medium";

  return (
    <>
      <SettingRow
        layout={layout}
        label={
          <span id={`${id}-days-label`} className={labelClassName}>
            {t("settings.openingHours.openOn")}
          </span>
        }
      >
        <fieldset
          aria-labelledby={`${id}-days-label`}
          aria-describedby={noDays ? `${id}-days-error` : undefined}
          className="min-w-0 space-y-2"
        >
          {/* Wraps rather than scrolls, so no day is ever hidden behind the column edge. */}
          <div className="relative flex min-h-9 flex-wrap items-center gap-x-3 gap-y-2">
            {ALL_ISO_WEEKDAYS.map((day) => (
              <div key={day} className="flex shrink-0 items-center gap-1.5">
                <Checkbox
                  id={`${id}-day-${day}`}
                  aria-labelledby={`${id}-day-${day}-label`}
                  checked={openDays.includes(day)}
                  disabled={disabled}
                  aria-invalid={noDays || undefined}
                  onCheckedChange={(checked) => {
                    // A closed day keeps its exception during the session; submission drops it.
                    if (!checked) withoutDraft(day);
                    openDaysField.onChange(
                      ALL_ISO_WEEKDAYS.filter((candidate) =>
                        candidate === day ? checked : openDays.includes(candidate),
                      ),
                    );
                  }}
                />
                <label
                  id={`${id}-day-${day}-label`}
                  htmlFor={`${id}-day-${day}`}
                  className="cursor-pointer text-sm"
                  title={formatIsoWeekday(day)}
                >
                  <span aria-hidden="true">{formatIsoWeekday(day, "short")}</span>
                  <span className="sr-only">{formatIsoWeekday(day)}</span>
                </label>
              </div>
            ))}
          </div>
          {noDays ? <FieldError id={`${id}-days-error`}>{t("settings.openingHours.errors.noDays")}</FieldError> : null}
        </fieldset>
      </SettingRow>

      <SettingRow
        layout={layout}
        label={
          <span id={`${id}-hours-label`} className={labelClassName}>
            {t("bookableItemDetails.fields.openingHours")}
          </span>
        }
      >
        <TimeRange
          labelledBy={`${id}-hours-label`}
          hours={shared}
          disabled={disabled}
          invalid={sharedInvalid}
          error={t("settings.errors.openingHours")}
          inputRefs={{ start: openingStart.props.ref, end: openingEnd.props.ref }}
          onChange={(next) => {
            if (next.start !== shared.start) openingStart.onChange(next.start);
            if (next.end !== shared.end) openingEnd.onChange(next.end);
          }}
        />
        <FieldDescription>
          {t("settings.fields.openingEndDescription", { midnight: formatWallClockTime("00:00") })}
        </FieldDescription>
        {showList && !noDays ? (
          <ul
            aria-label={t("settings.openingHours.hoursByDay")}
            aria-describedby={pendingDraft ? `${id}-pending-error` : undefined}
            className="@container divide-y rounded-sm border"
          >
            {selectedDays.map((day) => {
              const dayName = formatIsoWeekday(day);
              const exception = exceptions.find((candidate) => candidate.dayOfWeek === day);
              const own = exception ? { start: exception.start, end: exception.end } : undefined;
              const draft = drafts[day];
              const confirm = () => {
                if (!draft) return;
                const others = exceptions.filter((candidate) => candidate.dayOfWeek !== day);
                // Confirming the shared hours leaves the day following them.
                setExceptions(
                  draft.start === shared.start && draft.end === shared.end
                    ? others
                    : [...others, { dayOfWeek: day, ...draft }],
                );
                withoutDraft(day);
              };
              return (
                // Only the name-and-hours column wraps: a day's inputs move below its name once the row is too
                // narrow for both, while the icons keep their place on the first line.
                <li key={day} className="flex flex-nowrap items-start gap-3 px-3 py-2">
                  <div className="flex min-w-0 flex-1 flex-wrap items-start gap-x-3 gap-y-1">
                    <span
                      id={`${id}-row-${day}`}
                      className="flex min-h-9 min-w-20 shrink-0 items-center text-sm font-medium"
                    >
                      {dayName}
                    </span>
                    {draft ? (
                      // The basis is the inputs' minimum width, so they stay beside the name whenever they fit.
                      <div className="min-w-0 grow basis-50">
                        <TimeRange
                          labelledBy={`${id}-row-${day}`}
                          hours={draft}
                          disabled={disabled}
                          invalid={!validOpeningHours(draft.start, draft.end)}
                          error={t("settings.errors.openingHours")}
                          onChange={(next) => setDrafts((current) => ({ ...current, [day]: next }))}
                        />
                      </div>
                    ) : (
                      // Padding lines the read-out digits up with an input's text once inputs fit beside the name;
                      // narrower lists need that width to keep the read-out on the name's line.
                      <span
                        className={cn(
                          "flex min-h-9 grow items-center text-sm @sm:px-3",
                          own ? "font-bold" : "text-muted-foreground",
                        )}
                      >
                        {formatOpeningRange(own ?? shared)}
                      </span>
                    )}
                  </div>
                  {/* The first button stays mounted across states, so focus stays on it from edit to confirm. Two
                      equal columns keep it in place whether or not a second button follows. */}
                  <div className="grid min-h-9 shrink-0 grid-cols-2 items-center gap-1">
                    {draft
                      ? iconButton(
                          t("settings.openingHours.confirmDay", { day: dayName }),
                          <CheckIcon aria-hidden="true" />,
                          confirm,
                          !validOpeningHours(draft.start, draft.end),
                        )
                      : iconButton(
                          t("settings.openingHours.editDay", { day: dayName }),
                          <PencilIcon aria-hidden="true" />,
                          () => setDrafts((current) => ({ ...current, [day]: own ?? shared })),
                        )}
                    {draft
                      ? iconButton(
                          t("settings.openingHours.discardDay", {
                            day: dayName,
                          }),
                          <XIcon aria-hidden="true" />,
                          () => withoutDraft(day),
                        )
                      : own
                        ? iconButton(
                            t("settings.openingHours.useSharedDay", {
                              day: dayName,
                            }),
                            <Undo2Icon aria-hidden="true" />,
                            () => setExceptions(exceptions.filter((candidate) => candidate.dayOfWeek !== day)),
                          )
                        : null}
                  </div>
                </li>
              );
            })}
          </ul>
        ) : null}
        {showList && pendingDraft ? (
          <FieldError id={`${id}-pending-error`}>{t("settings.openingHours.errors.pendingDraft")}</FieldError>
        ) : null}
        {showList ? (
          // Resets every day to the shared hours; Cancel still restores the saved configuration.
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="self-start"
            disabled={disabled}
            onClick={() => {
              setDrafts({});
              setExceptions([]);
              setListOpen(false);
            }}
          >
            {t("settings.openingHours.useSameHours")}
          </Button>
        ) : (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="self-start"
            disabled={disabled}
            onClick={() => setListOpen(true)}
          >
            {t("settings.openingHours.setDifferentHours")}
          </Button>
        )}
      </SettingRow>
    </>
  );
}
