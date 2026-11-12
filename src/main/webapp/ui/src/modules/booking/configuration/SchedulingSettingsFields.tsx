import { type FormStore, getInput, useField } from "@formisch/react";
import { useId, useState } from "react";
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
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { DurationUnitInput, durationUnit } from "./DurationUnitInput";

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
      <DurationUnitInput
        id={id}
        minutes={minutes}
        unit={unit}
        onMinutesChange={field.onChange}
        onUnitChange={unitField.onChange}
        unitLabel={t("settings.fields.incrementUnit")}
        daysAvailable={daysAvailable}
        min={unit === "DAYS" ? 1 : 1 / factor}
        step={unit === "DAYS" ? 1 : "any"}
        required
        disabled={disabled}
        ref={field.props.ref}
        onFocus={field.props.onFocus}
        onBlur={field.props.onBlur}
        aria-invalid={problem ? true : undefined}
        aria-describedby={problem ? `${id}-error` : undefined}
      />
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
  const [maximumUnit, setMaximumUnit] = useState(() => durationUnit(values.maxBookingDurationMinutes ?? 0, unit));
  const invalid =
    values.maxBookingDurationMinutes !== undefined &&
    increment !== undefined &&
    !validMaximumForIncrement(values.maxBookingDurationMinutes, {
      slotGranularityMinutes: increment,
      incrementUnit: unit,
    });
  return (
    <Field>
      <DurationUnitInput
        id={id}
        minutes={numberInput(maximumDuration.input)}
        unit={maximumUnit}
        onMinutesChange={maximumDuration.onChange}
        onUnitChange={setMaximumUnit}
        unitLabel={t("settings.fields.maximumDurationUnit")}
        min={0}
        step="any"
        required
        disabled={disabled}
        ref={maximumDuration.props.ref}
        onFocus={maximumDuration.props.onFocus}
        onBlur={maximumDuration.props.onBlur}
        aria-invalid={invalid || undefined}
        aria-describedby={[`${id}-description`, invalid ? `${id}-error` : null].filter(Boolean).join(" ")}
      />
      <FieldDescription id={`${id}-description`}>{t("settings.fields.maximumDurationDescription")}</FieldDescription>
      {invalid ? (
        <FieldError id={`${id}-error`}>
          {unit === "DAYS" && increment !== undefined
            ? t("settings.errors.maximumDurationDays", {
                minutes: increment,
                days: increment / INCREMENT_UNIT_MINUTES.DAYS,
              })
            : t("settings.errors.maximumDuration", {
                increment: `${increment === undefined ? "" : increment / INCREMENT_UNIT_MINUTES[unit]} ${t(unit === "HOURS" ? "settings.fields.incrementHours" : "settings.fields.incrementMinutes")}`,
              })}
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
  const bufferBefore = useField(form, { path: ["bufferBeforeMinutes"] });
  const bufferAfter = useField(form, { path: ["bufferAfterMinutes"] });
  const allowDoubleBooking = useField(form, { path: ["allowDoubleBooking"] });
  const doubleBookingCheckboxProps = {
    id: `${id}-double-booking`,
    checked: allowDoubleBooking.input === true,
    disabled,
    inputRef: allowDoubleBooking.props.ref,
    onCheckedChange: (checked: boolean | "indeterminate") => allowDoubleBooking.onChange(checked),
  };

  if (layout === "inline") {
    const bufferBeforeErrorId = `${id}-buffer-before-error`;
    const bufferAfterErrorId = `${id}-buffer-after-error`;
    const bufferBeforeInvalid = Boolean(bufferBefore.errors?.length);
    const bufferAfterInvalid = Boolean(bufferAfter.errors?.length);
    const renderInlineBufferField = (
      field: typeof bufferBefore | typeof bufferAfter,
      direction: "Before" | "After",
      label: string,
      errorId: string,
      invalid: boolean,
    ) => {
      const fieldId = `${id}-buffer-${direction.toLowerCase()}`;
      return (
        <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
          <FieldLabel htmlFor={fieldId}>{label}</FieldLabel>
          <Field>
            <Input
              id={fieldId}
              type="number"
              min={0}
              max={MAX_BUFFER_MINUTES}
              step={1}
              required
              disabled={disabled}
              value={numberInput(field.input) ?? ""}
              ref={field.props.ref}
              onFocus={field.props.onFocus}
              onBlur={field.props.onBlur}
              aria-invalid={invalid || undefined}
              aria-describedby={invalid ? errorId : undefined}
              onChange={(event) => field.onChange(event.currentTarget.valueAsNumber)}
            />
            {invalid ? <FieldError id={errorId}>{t("settings.errors.buffer")}</FieldError> : null}
          </Field>
        </div>
      );
    };

    return (
      <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
        <fieldset className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
          <legend className="sr-only">{t("settings.fields.legend")}</legend>

          <OpeningHoursEditor form={form} disabled={disabled} layout="inline" />

          <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
            <FieldLabel htmlFor={`${id}-granularity`}>{t("bookableItemDetails.fields.granularity")}</FieldLabel>
            <TimeIncrementField form={form} id={`${id}-granularity`} disabled={disabled} />
          </div>

          <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
            <FieldLabel htmlFor={`${id}-maximum-duration`}>
              {t("bookableItemDetails.fields.maximumDuration")}
            </FieldLabel>
            <MaximumDurationField form={form} id={`${id}-maximum-duration`} disabled={disabled} />
          </div>

          {renderInlineBufferField(
            bufferBefore,
            "Before",
            t("bookableItemDetails.fields.bufferBefore"),
            bufferBeforeErrorId,
            bufferBeforeInvalid,
          )}
          {renderInlineBufferField(
            bufferAfter,
            "After",
            t("bookableItemDetails.fields.bufferAfter"),
            bufferAfterErrorId,
            bufferAfterInvalid,
          )}

          <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
            <FieldLabel htmlFor={`${id}-double-booking`}>{t("bookableItemDetails.fields.doubleBooking")}</FieldLabel>
            <div className="flex min-h-9 items-center">
              <Checkbox {...doubleBookingCheckboxProps} />
            </div>
          </div>
        </fieldset>
      </div>
    );
  }

  return (
    <fieldset className="space-y-6">
      <legend className="text-base font-medium">{t("settings.fields.legend")}</legend>
      <div className="space-y-2">
        <FieldLabel htmlFor={`${id}-granularity`}>{t("settings.fields.granularity")}</FieldLabel>
        <TimeIncrementField form={form} id={`${id}-granularity`} disabled={disabled} />
      </div>
      <OpeningHoursEditor form={form} disabled={disabled} layout="stacked" />
      {(
        [
          [bufferBefore, "Before", t("settings.fields.bufferBefore")],
          [bufferAfter, "After", t("settings.fields.bufferAfter")],
        ] as const
      ).map(([buffer, direction, label]) => {
        const bufferId = `${id}-buffer-${direction.toLowerCase()}`;
        const errorId = `${bufferId}-error`;
        const invalid = Boolean(buffer.errors?.length);
        return (
          <Field key={direction}>
            <FieldLabel htmlFor={bufferId}>{label}</FieldLabel>
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
              aria-invalid={invalid || undefined}
              aria-describedby={invalid ? errorId : undefined}
              onChange={(event) => buffer.onChange(event.currentTarget.valueAsNumber)}
            />
            {invalid ? <FieldError id={errorId}>{t("settings.errors.buffer")}</FieldError> : null}
          </Field>
        );
      })}
      <div className="space-y-2">
        <FieldLabel htmlFor={`${id}-maximum-duration`}>{t("settings.fields.maximumDuration")}</FieldLabel>
        <MaximumDurationField form={form} id={`${id}-maximum-duration`} disabled={disabled} />
      </div>
      <Field orientation="horizontal">
        <Checkbox {...doubleBookingCheckboxProps} />
        <FieldLabel htmlFor={`${id}-double-booking`}>{t("settings.fields.allowDoubleBooking")}</FieldLabel>
      </Field>
    </fieldset>
  );
}
