import { type FormStore, getInput, useField } from "@formisch/react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import { OpeningHoursEditor } from "@/modules/booking/configuration/OpeningHoursEditor";
import { MAX_BUFFER_MINUTES, type SchedulingSettings } from "@/modules/booking/configuration/schedulingSettingsSchema";
import {
  INCREMENT_UNIT_MINUTES,
  INCREMENT_UNITS,
  type IncrementSettings,
  type IncrementUnit,
  openAllDayEveryDay,
  validIncrementForSchedule,
  validMaximumForIncrement,
} from "@/modules/booking/domain/bookingIncrement";
import { MAX_BOOKING_DURATION_MINUTES, type OpeningSchedule } from "@/modules/booking/domain/bookingOpeningHours";
import { formData } from "@/modules/common/collection-form/formData";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";

function numberInput(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function incrementUnitInput(value: unknown): IncrementUnit {
  return INCREMENT_UNITS.find((unit) => unit === value) ?? "MINUTES";
}

/** Why an increment cannot be saved; null when `validIncrementForSchedule` accepts it. */
export type IncrementProblem = "range" | "wholeMinutes" | "wholeDays" | "daysNeedFullWeek" | "opening";

export function incrementProblem(settings: OpeningSchedule & IncrementSettings): IncrementProblem | null {
  if (validIncrementForSchedule(settings)) return null;
  const minutes = settings.slotGranularityMinutes;
  if (!Number.isFinite(minutes) || minutes < 1 || minutes > MAX_BOOKING_DURATION_MINUTES) return "range";
  if (settings.incrementUnit === "DAYS") {
    return minutes % INCREMENT_UNIT_MINUTES.DAYS === 0 ? "daysNeedFullWeek" : "wholeDays";
  }
  if (!Number.isInteger(minutes)) return "wholeMinutes";
  return "opening";
}

const INCREMENT_PROBLEM_KEYS = {
  range: "settings.errors.incrementRange",
  wholeMinutes: "settings.errors.incrementWholeMinutes",
  wholeDays: "settings.errors.incrementWholeDays",
  daysNeedFullWeek: "settings.errors.incrementDaysNeedFullWeek",
  opening: "settings.errors.incrementOpening",
} as const satisfies Record<IncrementProblem, string>;

const INCREMENT_UNIT_LABEL_KEYS = {
  MINUTES: "settings.fields.incrementMinutes",
  HOURS: "settings.fields.incrementHours",
  DAYS: "settings.fields.incrementDays",
} as const satisfies Record<IncrementUnit, string>;

// Unit conversion can leave float noise (0.35 days = 503.99999999999994); fractional minutes stay invalid.
const wholeMinutes = (value: number) => Number(value.toFixed(6));

/** The increment number and unit remain distinct, including 24 Hours and 1 Day. */
function TimeIncrementField({ form, id, disabled }: { form: FormStore; id: string; disabled: boolean }) {
  const { t } = useTranslation("booking");
  const field = useField(form, { path: ["slotGranularityMinutes"] });
  const unitField = useField(form, { path: ["incrementUnit"] });
  // Reading the whole input subscribes this field to the opening hours that decide Days and the fit checks.
  const values = getInput(form) as SchedulingSettings;
  const minutes = numberInput(field.input);
  const unit = incrementUnitInput(unitField.input);
  const factor = INCREMENT_UNIT_MINUTES[unit];
  const daysAvailable = openAllDayEveryDay(values);
  const problem: IncrementProblem | null =
    minutes === undefined
      ? field.errors?.length
        ? "range"
        : null
      : incrementProblem({
          ...values,
          slotGranularityMinutes: minutes,
          incrementUnit: unit,
        });
  return (
    <Field>
      <div className="flex flex-wrap items-center gap-2">
        <Input
          id={id}
          type="number"
          min={unit === "DAYS" ? 1 : 1 / factor}
          max={MAX_BOOKING_DURATION_MINUTES / factor}
          step={unit === "DAYS" ? 1 : "any"}
          required
          disabled={disabled}
          className="w-28"
          value={minutes === undefined ? "" : wholeMinutes(minutes / factor)}
          ref={field.props.ref}
          onFocus={field.props.onFocus}
          onBlur={field.props.onBlur}
          aria-invalid={problem ? true : undefined}
          aria-describedby={problem ? `${id}-error` : undefined}
          onChange={(event) =>
            field.onChange(
              event.currentTarget.value === "" ? undefined : wholeMinutes(event.currentTarget.valueAsNumber * factor),
            )
          }
        />
        <ButtonGroup aria-label={t("settings.fields.incrementUnit")}>
          {INCREMENT_UNITS.map((candidate) => {
            const unavailable = candidate === "DAYS" && !daysAvailable;
            return (
              <Button
                key={candidate}
                type="button"
                variant={unit === candidate ? "default" : "outline"}
                aria-pressed={unit === candidate}
                // Stays focusable while unavailable, so its reason can be read.
                disabled={disabled || unavailable}
                focusableWhenDisabled={unavailable && !disabled}
                aria-describedby={unavailable ? `${id}-days-unavailable` : undefined}
                title={unavailable ? t("settings.fields.incrementDaysUnavailable") : undefined}
                className="aria-disabled:cursor-not-allowed aria-disabled:opacity-50"
                onClick={() => {
                  if (unavailable || candidate === unit) return;
                  // The typed number stays; only its unit changes.
                  field.onChange(
                    minutes === undefined
                      ? undefined
                      : wholeMinutes((minutes / factor) * INCREMENT_UNIT_MINUTES[candidate]),
                  );
                  unitField.onChange(candidate);
                }}
              >
                {t(INCREMENT_UNIT_LABEL_KEYS[candidate])}
              </Button>
            );
          })}
        </ButtonGroup>
        {/* Inside the row: Field widens its direct sr-only children, which would overflow narrow pages. */}
        {daysAvailable ? null : (
          <span id={`${id}-days-unavailable`} className="sr-only">
            {t("settings.fields.incrementDaysUnavailable")}
          </span>
        )}
      </div>
      {problem ? <FieldError id={`${id}-error`}>{t(INCREMENT_PROBLEM_KEYS[problem])}</FieldError> : null}
    </Field>
  );
}

/** The item maximum in elapsed minutes; Day increments need at least their nominal minutes, without divisibility. */
function MaximumDurationField({ form, id, disabled }: { form: FormStore; id: string; disabled: boolean }) {
  const { t } = useTranslation("booking");
  const maximumDuration = useField(form, { path: ["maxBookingDurationMinutes"] });
  const values = formData<SchedulingSettings>(form);
  const increment = numberInput(values.slotGranularityMinutes);
  const unit = incrementUnitInput(values.incrementUnit);
  const invalid =
    values.maxBookingDurationMinutes !== undefined &&
    increment !== undefined &&
    !validMaximumForIncrement(values.maxBookingDurationMinutes, {
      slotGranularityMinutes: increment,
      incrementUnit: unit,
    });
  return (
    <Field>
      <Input
        id={id}
        type="number"
        min={0}
        max={MAX_BOOKING_DURATION_MINUTES}
        step={unit !== "DAYS" && increment !== undefined && Number.isInteger(increment) ? increment : 1}
        required
        disabled={disabled}
        value={numberInput(maximumDuration.input) ?? ""}
        ref={maximumDuration.props.ref}
        onFocus={maximumDuration.props.onFocus}
        onBlur={maximumDuration.props.onBlur}
        aria-invalid={invalid || undefined}
        aria-describedby={[`${id}-description`, invalid ? `${id}-error` : null].filter(Boolean).join(" ")}
        onChange={(event) => maximumDuration.onChange(event.currentTarget.valueAsNumber)}
      />
      <FieldDescription id={`${id}-description`}>{t("settings.fields.maximumDurationDescription")}</FieldDescription>
      {invalid ? (
        <FieldError id={`${id}-error`}>
          {unit === "DAYS" && increment !== undefined
            ? t("settings.errors.maximumDurationDays", {
                minutes: increment,
                days: increment / INCREMENT_UNIT_MINUTES.DAYS,
              })
            : t("settings.errors.maximumDuration")}
        </FieldError>
      ) : null}
    </Field>
  );
}

/** Scheduling controls shared by add-item, edit-item, and global-default forms. */
export function SchedulingSettingsFields({
  form,
  disabled = false,
  layout = "stacked",
}: {
  form: FormStore;
  disabled?: boolean;
  layout?: "stacked" | "inline";
}) {
  const { t } = useTranslation("booking");
  const id = useId();
  const buffer = useField(form, { path: ["bufferMinutes"] });
  const allowDoubleBooking = useField(form, { path: ["allowDoubleBooking"] });
  const bufferId = `${id}-buffer`;
  const errorId = `${bufferId}-error`;
  const bufferControl = (
    <div className="space-y-2">
      <Input
        id={bufferId}
        type="number"
        min={0}
        max={MAX_BUFFER_MINUTES}
        step={1}
        required
        disabled={disabled}
        value={numberInput(buffer.input) ?? ""}
        ref={buffer.props.ref}
        onFocus={buffer.props.onFocus}
        onBlur={buffer.props.onBlur}
        aria-invalid={Boolean(buffer.errors?.length) || undefined}
        aria-describedby={buffer.errors?.length ? errorId : undefined}
        onChange={(event) => buffer.onChange(event.currentTarget.valueAsNumber)}
      />
      {buffer.errors?.length ? <FieldError id={errorId}>{t("settings.errors.buffer")}</FieldError> : null}
    </div>
  );
  const doubleBooking = (
    <Checkbox
      id={`${id}-double-booking`}
      checked={allowDoubleBooking.input === true}
      disabled={disabled}
      inputRef={allowDoubleBooking.props.ref}
      onCheckedChange={(checked) => allowDoubleBooking.onChange(checked)}
    />
  );
  const inline = layout === "inline";
  const rowClass = inline ? RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME : "space-y-2";
  return (
    <div className={inline ? RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME : undefined}>
      <fieldset className={inline ? `${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4` : "space-y-6"}>
        <legend className={inline ? "sr-only" : "text-base font-medium"}>{t("settings.fields.legend")}</legend>
        <OpeningHoursEditor form={form} disabled={disabled} layout={layout} />
        <div className={rowClass}>
          <FieldLabel htmlFor={`${id}-granularity`}>
            {inline ? t("bookableItemDetails.fields.granularity") : t("settings.fields.granularity")}
          </FieldLabel>
          <TimeIncrementField form={form} id={`${id}-granularity`} disabled={disabled} />
        </div>
        <div className={rowClass}>
          <FieldLabel htmlFor={bufferId}>{t("settings.fields.buffer")}</FieldLabel>
          {bufferControl}
        </div>
        <div className={rowClass}>
          <FieldLabel htmlFor={`${id}-maximum-duration`}>{t("settings.fields.maximumDuration")}</FieldLabel>
          <MaximumDurationField form={form} id={`${id}-maximum-duration`} disabled={disabled} />
        </div>
        <div className={rowClass}>
          <FieldLabel htmlFor={`${id}-double-booking`}>
            {inline ? t("bookableItemDetails.fields.doubleBooking") : t("settings.fields.allowDoubleBooking")}
          </FieldLabel>
          <div className="flex min-h-9 items-center">{doubleBooking}</div>
        </div>
      </fieldset>
    </div>
  );
}
