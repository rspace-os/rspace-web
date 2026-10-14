import { type FormStore, getInput, useField } from "@formisch/react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { OpeningHoursEditor } from "@/modules/booking/configuration/OpeningHoursEditor";
import { bookingApiV2Headers, bookingApiV2JsonHeaders } from "@/modules/booking/domain/apiV2";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import {
  type BookingDisplayPreferencesInput,
  BookingDisplayPreferencesInputSchema,
  BookingTimezoneModeSchema,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  ALL_ISO_WEEKDAYS,
  MAX_BOOKING_DURATION_MINUTES,
  OpeningHoursSchema,
  validOpeningHours,
} from "@/modules/booking/domain/bookingOpeningHours";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";

export const MAX_BUFFER_MINUTES = 10_080;
export { MAX_BOOKING_DURATION_MINUTES, validOpeningHours };
export const schedulingSettingsFieldNames = [
  "slotGranularityMinutes",
  "openingHours",
  "bufferBeforeMinutes",
  "bufferAfterMinutes",
  "maxBookingDurationMinutes",
  "allowDoubleBooking",
] as const;
export const schedulingSettingsEntries = {
  slotGranularityMinutes: v.picklist([1, 5, 10, 15]),
  openingHours: OpeningHoursSchema,
  bufferBeforeMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BUFFER_MINUTES)),
  bufferAfterMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BUFFER_MINUTES)),
  maxBookingDurationMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BOOKING_DURATION_MINUTES)),
  allowDoubleBooking: v.boolean(),
};
const bookingDisplayPreferencesEntries = {
  availabilityWindowStart: v.string(),
  availabilityWindowEnd: v.string(),
  timezoneMode: BookingTimezoneModeSchema,
  customTimezone: v.nullable(v.string()),
  institutionTimezone: v.string(),
};

export const SchedulingSettingsSchema = v.pipe(
  v.object(schedulingSettingsEntries),
  v.forward(
    v.check((settings) =>
      validMaximumBookingDuration(settings.maxBookingDurationMinutes, settings.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export const BookingSettingsSchema = v.pipe(
  v.object({
    ...schedulingSettingsEntries,
    ...bookingDisplayPreferencesEntries,
  }),
  v.forward(
    v.check((settings) =>
      validMaximumBookingDuration(settings.maxBookingDurationMinutes, settings.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export const BookingAdminSettingsSchema = v.pipe(
  v.object({
    ...schedulingSettingsEntries,
    ...bookingDisplayPreferencesEntries,
    defaultSharedWith: v.picklist(["ALL_USERS", "SELECTED", "ONLY_ME"]),
    selectedAccessGrantees: v.array(
      v.object({
        kind: v.picklist(["USER", "GROUP"]),
        id: v.number(),
        key: v.string(),
        name: v.string(),
        detail: v.optional(v.nullable(v.string())),
        available: v.boolean(),
      }),
    ),
    configurationVersion: v.number(),
  }),
  v.forward(
    v.check((settings) =>
      validMaximumBookingDuration(settings.maxBookingDurationMinutes, settings.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export type SchedulingSettings = v.InferOutput<typeof SchedulingSettingsSchema>;
export type BookingSettings = v.InferOutput<typeof BookingSettingsSchema>;
export type BookingAdminSettings = v.InferOutput<typeof BookingAdminSettingsSchema>;
export type BookingSettingsInput = SchedulingSettings & BookingDisplayPreferencesInput;

export const DEFAULT_SCHEDULING_SETTINGS: SchedulingSettings = {
  slotGranularityMinutes: 5,
  openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

export function validMaximumBookingDuration(maximumMinutes: number, granularityMinutes: number): boolean {
  return (
    maximumMinutes === 0 ||
    (maximumMinutes >= granularityMinutes &&
      maximumMinutes <= MAX_BOOKING_DURATION_MINUTES &&
      maximumMinutes % granularityMinutes === 0)
  );
}

export async function loadBookingSettings(token: string, signal?: AbortSignal): Promise<BookingSettings> {
  const response = await fetch("/api/v2/booking-settings", {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking settings request failed with status ${response.status}`);
  return parseOrThrow(BookingSettingsSchema, await response.json());
}

export async function loadBookingAdminSettings(token: string, signal?: AbortSignal): Promise<BookingAdminSettings> {
  const response = await fetch("/api/v2/booking-settings/admin", {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking admin settings request failed with status ${response.status}`);
  return parseOrThrow(BookingAdminSettingsSchema, await response.json());
}

export async function saveBookingSettings(
  input: BookingSettingsInput,
  configurationVersion: number,
  token: string,
): Promise<BookingAdminSettings> {
  const scheduling = parseOrThrow(SchedulingSettingsSchema, input);
  const display = parseOrThrow(BookingDisplayPreferencesInputSchema, {
    availabilityWindowStart: input.availabilityWindowStart,
    availabilityWindowEnd: input.availabilityWindowEnd,
    timezoneMode: input.timezoneMode,
    customTimezone: input.customTimezone,
  });
  const response = await fetch("/api/v2/booking-settings/admin", {
    method: "PATCH",
    headers: bookingApiV2JsonHeaders(token),
    body: JSON.stringify({
      ...scheduling,
      ...display,
      configurationVersion,
    }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
  return parseOrThrow(BookingAdminSettingsSchema, await response.json());
}

function numberInput(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
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
  const granularity = useField(form, { path: ["slotGranularityMinutes"] });
  const bufferBefore = useField(form, { path: ["bufferBeforeMinutes"] });
  const bufferAfter = useField(form, { path: ["bufferAfterMinutes"] });
  const maximumDuration = useField(form, { path: ["maxBookingDurationMinutes"] });
  const allowDoubleBooking = useField(form, { path: ["allowDoubleBooking"] });
  const doubleBookingCheckboxProps = {
    id: `${id}-double-booking`,
    checked: allowDoubleBooking.input === true,
    disabled,
    inputRef: allowDoubleBooking.props.ref,
    onCheckedChange: (checked: boolean | "indeterminate") => allowDoubleBooking.onChange(checked),
  };
  const values = getInput(form) as Partial<SchedulingSettings>;
  const mixedBuffers = values.bufferBeforeMinutes !== values.bufferAfterMinutes;
  const maximumDurationInvalid =
    values.maxBookingDurationMinutes !== undefined &&
    values.slotGranularityMinutes !== undefined &&
    !validMaximumBookingDuration(values.maxBookingDurationMinutes, values.slotGranularityMinutes);

  if (layout === "inline") {
    const granularityErrorId = `${id}-granularity-error`;
    const bufferBeforeErrorId = `${id}-buffer-before-error`;
    const bufferAfterErrorId = `${id}-buffer-after-error`;
    const maximumDurationDescriptionId = `${id}-maximum-duration-description`;
    const maximumDurationErrorId = `${id}-maximum-duration-error`;
    const granularityInvalid = Boolean(granularity.errors?.length);
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
            <Field>
              <select
                id={`${id}-granularity`}
                className="h-9 w-full rounded-sm bg-input/50 px-3 text-sm"
                value={numberInput(granularity.input) ?? ""}
                disabled={disabled}
                ref={granularity.props.ref}
                onFocus={granularity.props.onFocus}
                onBlur={granularity.props.onBlur}
                aria-invalid={granularityInvalid || undefined}
                aria-describedby={granularityInvalid ? granularityErrorId : undefined}
                onChange={(event) => granularity.onChange(Number(event.currentTarget.value))}
              >
                {[1, 5, 10, 15].map((minutes) => (
                  <option key={minutes} value={minutes}>
                    {t("settings.fields.granularityOption", { count: minutes })}
                  </option>
                ))}
              </select>
              {granularityInvalid ? (
                <FieldError id={granularityErrorId}>{t("settings.errors.granularity")}</FieldError>
              ) : null}
            </Field>
          </div>

          <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
            <FieldLabel htmlFor={`${id}-maximum-duration`}>
              {t("bookableItemDetails.fields.maximumDuration")}
            </FieldLabel>
            <Field>
              <Input
                id={`${id}-maximum-duration`}
                type="number"
                min={0}
                max={MAX_BOOKING_DURATION_MINUTES}
                step={numberInput(granularity.input) ?? 1}
                required
                disabled={disabled}
                value={numberInput(maximumDuration.input) ?? ""}
                ref={maximumDuration.props.ref}
                onFocus={maximumDuration.props.onFocus}
                onBlur={maximumDuration.props.onBlur}
                aria-invalid={maximumDurationInvalid || undefined}
                aria-describedby={
                  maximumDurationInvalid
                    ? `${maximumDurationDescriptionId} ${maximumDurationErrorId}`
                    : maximumDurationDescriptionId
                }
                onChange={(event) => maximumDuration.onChange(event.currentTarget.valueAsNumber)}
              />
              <FieldDescription id={maximumDurationDescriptionId}>
                {t("settings.fields.maximumDurationDescription")}
              </FieldDescription>
              {maximumDurationInvalid ? (
                <FieldError id={maximumDurationErrorId}>{t("settings.errors.maximumDuration")}</FieldError>
              ) : null}
            </Field>
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
      <Field>
        <FieldLabel htmlFor={`${id}-granularity`}>{t("settings.fields.granularity")}</FieldLabel>
        <select
          id={`${id}-granularity`}
          className="h-9 w-full rounded-sm bg-input/50 px-3 text-sm"
          value={numberInput(granularity.input) ?? ""}
          disabled={disabled}
          ref={granularity.props.ref}
          onFocus={granularity.props.onFocus}
          onBlur={granularity.props.onBlur}
          onChange={(event) => granularity.onChange(Number(event.currentTarget.value))}
        >
          {[1, 5, 10, 15].map((minutes) => (
            <option key={minutes} value={minutes}>
              {t("settings.fields.granularityOption", { count: minutes })}
            </option>
          ))}
        </select>
      </Field>
      <OpeningHoursEditor form={form} disabled={disabled} layout="stacked" />
      <Field>
        <FieldLabel htmlFor={`${id}-buffer`}>{t("settings.fields.buffer")}</FieldLabel>
        <Input
          id={`${id}-buffer`}
          type="number"
          min={0}
          max={MAX_BUFFER_MINUTES}
          step={1}
          required={!mixedBuffers}
          disabled={disabled}
          value={mixedBuffers ? "" : (numberInput(bufferBefore.input) ?? "")}
          aria-describedby={mixedBuffers ? `${id}-buffer-mixed` : undefined}
          onChange={(event) => {
            const value = event.currentTarget.value === "" ? undefined : event.currentTarget.valueAsNumber;
            bufferBefore.onChange(value);
            bufferAfter.onChange(value);
          }}
        />
        {mixedBuffers ? (
          <FieldDescription id={`${id}-buffer-mixed`}>{t("settings.fields.bufferMixed")}</FieldDescription>
        ) : null}
      </Field>
      <Field>
        <FieldLabel htmlFor={`${id}-maximum-duration`}>{t("settings.fields.maximumDuration")}</FieldLabel>
        <Input
          id={`${id}-maximum-duration`}
          type="number"
          min={0}
          max={MAX_BOOKING_DURATION_MINUTES}
          step={numberInput(granularity.input) ?? 1}
          required
          disabled={disabled}
          value={numberInput(maximumDuration.input) ?? ""}
          ref={maximumDuration.props.ref}
          onFocus={maximumDuration.props.onFocus}
          onBlur={maximumDuration.props.onBlur}
          aria-describedby={`${id}-maximum-duration-description`}
          onChange={(event) => maximumDuration.onChange(event.currentTarget.valueAsNumber)}
        />
        <FieldDescription id={`${id}-maximum-duration-description`}>
          {t("settings.fields.maximumDurationDescription")}
        </FieldDescription>
        {maximumDurationInvalid ? <FieldError>{t("settings.errors.maximumDuration")}</FieldError> : null}
      </Field>
      <Field orientation="horizontal">
        <Checkbox {...doubleBookingCheckboxProps} />
        <FieldLabel htmlFor={`${id}-double-booking`}>{t("settings.fields.allowDoubleBooking")}</FieldLabel>
      </Field>
    </fieldset>
  );
}
