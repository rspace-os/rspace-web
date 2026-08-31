import { type FormStore, getInput, useField } from "@formisch/react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import {
  type BookingDisplayPreferencesInput,
  BookingDisplayPreferencesInputSchema,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";

export const MAX_BUFFER_MINUTES = 10_080;
export const MAX_BOOKING_DURATION_MINUTES = 527_040;
export const schedulingSettingsFieldNames = [
  "slotGranularityMinutes",
  "openingStart",
  "openingEnd",
  "bufferBeforeMinutes",
  "bufferAfterMinutes",
  "maxBookingDurationMinutes",
  "allowDoubleBooking",
] as const;
const WALL_TIME = /^(?:[01]\d|2[0-3]):[0-5]\d$/;

export const schedulingSettingsEntries = {
  slotGranularityMinutes: v.picklist([1, 5, 15]),
  openingStart: v.pipe(v.string(), v.regex(WALL_TIME)),
  openingEnd: v.union([v.pipe(v.string(), v.regex(WALL_TIME)), v.literal("24:00")]),
  bufferBeforeMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BUFFER_MINUTES)),
  bufferAfterMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BUFFER_MINUTES)),
  maxBookingDurationMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BOOKING_DURATION_MINUTES)),
  allowDoubleBooking: v.boolean(),
};

export const SchedulingSettingsSchema = v.pipe(
  v.object(schedulingSettingsEntries),
  v.forward(
    v.check((settings) => validOpeningHours(settings.openingStart, settings.openingEnd)),
    ["openingEnd"],
  ),
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
    availabilityWindowStart: v.string(),
    availabilityWindowEnd: v.string(),
    timezoneMode: v.picklist(["BROWSER", "INSTITUTION", "CUSTOM"]),
    customTimezone: v.nullable(v.string()),
    institutionTimezone: v.string(),
    configurationVersion: v.number(),
  }),
  v.forward(
    v.check((settings) => validOpeningHours(settings.openingStart, settings.openingEnd)),
    ["openingEnd"],
  ),
  v.forward(
    v.check((settings) =>
      validMaximumBookingDuration(settings.maxBookingDurationMinutes, settings.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export type SchedulingSettings = v.InferOutput<typeof SchedulingSettingsSchema>;
export type BookingSettings = v.InferOutput<typeof BookingSettingsSchema>;
export type BookingSettingsInput = SchedulingSettings & BookingDisplayPreferencesInput;

export const BookingSettingsInputSchema = v.intersect([SchedulingSettingsSchema, BookingDisplayPreferencesInputSchema]);

export const DEFAULT_SCHEDULING_SETTINGS: SchedulingSettings = {
  slotGranularityMinutes: 5,
  openingStart: "00:00",
  openingEnd: "24:00",
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

export function validOpeningHours(start: string, end: string): boolean {
  if (!WALL_TIME.test(start)) return false;
  if (end === "24:00") return start === "00:00";
  return WALL_TIME.test(end) && start < end;
}

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
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Booking settings request failed with status ${response.status}`);
  return parseOrThrow(BookingSettingsSchema, await response.json());
}

export async function saveBookingSettings(
  input: BookingSettingsInput,
  configurationVersion: number,
  token: string,
): Promise<BookingSettings> {
  const scheduling = parseOrThrow(SchedulingSettingsSchema, input);
  const display = parseOrThrow(BookingDisplayPreferencesInputSchema, {
    availabilityWindowStart: input.availabilityWindowStart,
    availabilityWindowEnd: input.availabilityWindowEnd,
    timezoneMode: input.timezoneMode,
    customTimezone: input.customTimezone,
  });
  const response = await fetch("/api/v2/booking-settings", {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
    },
    body: JSON.stringify({ ...scheduling, ...display, configurationVersion }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
  return parseOrThrow(BookingSettingsSchema, await response.json());
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
  const openingStart = useField(form, { path: ["openingStart"] });
  const openingEnd = useField(form, { path: ["openingEnd"] });
  const bufferBefore = useField(form, { path: ["bufferBeforeMinutes"] });
  const bufferAfter = useField(form, { path: ["bufferAfterMinutes"] });
  const maximumDuration = useField(form, { path: ["maxBookingDurationMinutes"] });
  const allowDoubleBooking = useField(form, { path: ["allowDoubleBooking"] });
  const values = getInput(form) as Partial<SchedulingSettings>;
  const fullDay = values.openingStart === "00:00" && values.openingEnd === "24:00";
  const mixedBuffers = values.bufferBeforeMinutes !== values.bufferAfterMinutes;
  const openingInvalid =
    Boolean(values.openingStart && values.openingEnd) &&
    !validOpeningHours(values.openingStart ?? "", values.openingEnd ?? "");
  const maximumDurationInvalid =
    values.maxBookingDurationMinutes !== undefined &&
    values.slotGranularityMinutes !== undefined &&
    !validMaximumBookingDuration(values.maxBookingDurationMinutes, values.slotGranularityMinutes);

  if (layout === "inline") {
    const openingErrorId = `${id}-opening-error`;
    const granularityErrorId = `${id}-granularity-error`;
    const bufferBeforeErrorId = `${id}-buffer-before-error`;
    const bufferAfterErrorId = `${id}-buffer-after-error`;
    const maximumDurationDescriptionId = `${id}-maximum-duration-description`;
    const maximumDurationErrorId = `${id}-maximum-duration-error`;
    const granularityInvalid = Boolean(granularity.errors?.length);
    const bufferBeforeInvalid = Boolean(bufferBefore.errors?.length);
    const bufferAfterInvalid = Boolean(bufferAfter.errors?.length);
    const openingStartInvalid = openingInvalid || Boolean(openingStart.errors?.length);
    const openingEndInvalid = openingInvalid || Boolean(openingEnd.errors?.length);

    return (
      <fieldset className="grid gap-x-8 gap-y-4 sm:col-span-2 sm:grid-cols-subgrid">
        <legend className="sr-only">{t("settings.fields.legend")}</legend>

        <div className="grid gap-1 sm:contents">
          <FieldLabel htmlFor={`${id}-opening-start`}>{t("bookableItemDetails.fields.openingHours")}</FieldLabel>
          <div className="space-y-3">
            <Field orientation="horizontal">
              <Checkbox
                id={`${id}-full-day`}
                checked={fullDay}
                disabled={disabled}
                onCheckedChange={(checked) => {
                  openingStart.onChange(checked ? "00:00" : "08:00");
                  openingEnd.onChange(checked ? "24:00" : "18:00");
                }}
              />
              <FieldLabel htmlFor={`${id}-full-day`}>{t("settings.fields.fullDay")}</FieldLabel>
            </Field>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor={`${id}-opening-start`}>{t("settings.fields.openingStart")}</FieldLabel>
                <Input
                  id={`${id}-opening-start`}
                  type="time"
                  step={60}
                  required
                  disabled={disabled || fullDay}
                  value={typeof openingStart.input === "string" ? openingStart.input : ""}
                  ref={openingStart.props.ref}
                  onFocus={openingStart.props.onFocus}
                  onBlur={openingStart.props.onBlur}
                  aria-invalid={openingStartInvalid || undefined}
                  aria-describedby={openingStartInvalid ? openingErrorId : undefined}
                  onChange={(event) => openingStart.onChange(event.currentTarget.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor={`${id}-opening-end`}>{t("settings.fields.openingEnd")}</FieldLabel>
                <Input
                  id={`${id}-opening-end`}
                  type="time"
                  step={60}
                  required
                  disabled={disabled || fullDay}
                  value={fullDay ? "" : typeof openingEnd.input === "string" ? openingEnd.input : ""}
                  ref={openingEnd.props.ref}
                  onFocus={openingEnd.props.onFocus}
                  onBlur={openingEnd.props.onBlur}
                  aria-invalid={openingEndInvalid || undefined}
                  aria-describedby={openingEndInvalid ? openingErrorId : undefined}
                  onChange={(event) => openingEnd.onChange(event.currentTarget.value)}
                />
              </Field>
            </div>
            {openingStartInvalid || openingEndInvalid ? (
              <FieldError id={openingErrorId}>{t("settings.errors.openingHours")}</FieldError>
            ) : null}
          </div>
        </div>

        <div className="grid gap-1 sm:contents">
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
              {[1, 5, 15].map((minutes) => (
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

        <div className="grid gap-1 sm:contents">
          <FieldLabel htmlFor={`${id}-maximum-duration`}>{t("bookableItemDetails.fields.maximumDuration")}</FieldLabel>
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

        <div className="grid gap-1 sm:contents">
          <FieldLabel htmlFor={`${id}-buffer-before`}>{t("bookableItemDetails.fields.bufferBefore")}</FieldLabel>
          <Field>
            <Input
              id={`${id}-buffer-before`}
              type="number"
              min={0}
              max={MAX_BUFFER_MINUTES}
              step={1}
              required
              disabled={disabled}
              value={numberInput(bufferBefore.input) ?? ""}
              ref={bufferBefore.props.ref}
              onFocus={bufferBefore.props.onFocus}
              onBlur={bufferBefore.props.onBlur}
              aria-invalid={bufferBeforeInvalid || undefined}
              aria-describedby={bufferBeforeInvalid ? bufferBeforeErrorId : undefined}
              onChange={(event) => bufferBefore.onChange(event.currentTarget.valueAsNumber)}
            />
            {bufferBeforeInvalid ? (
              <FieldError id={bufferBeforeErrorId}>{t("settings.errors.buffer")}</FieldError>
            ) : null}
          </Field>
        </div>

        <div className="grid gap-1 sm:contents">
          <FieldLabel htmlFor={`${id}-buffer-after`}>{t("bookableItemDetails.fields.bufferAfter")}</FieldLabel>
          <Field>
            <Input
              id={`${id}-buffer-after`}
              type="number"
              min={0}
              max={MAX_BUFFER_MINUTES}
              step={1}
              required
              disabled={disabled}
              value={numberInput(bufferAfter.input) ?? ""}
              ref={bufferAfter.props.ref}
              onFocus={bufferAfter.props.onFocus}
              onBlur={bufferAfter.props.onBlur}
              aria-invalid={bufferAfterInvalid || undefined}
              aria-describedby={bufferAfterInvalid ? bufferAfterErrorId : undefined}
              onChange={(event) => bufferAfter.onChange(event.currentTarget.valueAsNumber)}
            />
            {bufferAfterInvalid ? <FieldError id={bufferAfterErrorId}>{t("settings.errors.buffer")}</FieldError> : null}
          </Field>
        </div>

        <div className="grid gap-1 sm:contents">
          <FieldLabel htmlFor={`${id}-double-booking`}>{t("bookableItemDetails.fields.doubleBooking")}</FieldLabel>
          <div className="flex min-h-9 items-center">
            <Checkbox
              id={`${id}-double-booking`}
              checked={allowDoubleBooking.input === true}
              disabled={disabled}
              inputRef={allowDoubleBooking.props.ref}
              onCheckedChange={(checked) => allowDoubleBooking.onChange(checked)}
            />
          </div>
        </div>
      </fieldset>
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
          {[1, 5, 15].map((minutes) => (
            <option key={minutes} value={minutes}>
              {t("settings.fields.granularityOption", { count: minutes })}
            </option>
          ))}
        </select>
      </Field>
      <Field orientation="horizontal">
        <Checkbox
          id={`${id}-full-day`}
          checked={fullDay}
          disabled={disabled}
          onCheckedChange={(checked) => {
            openingStart.onChange(checked ? "00:00" : "08:00");
            openingEnd.onChange(checked ? "24:00" : "18:00");
          }}
        />
        <FieldLabel htmlFor={`${id}-full-day`}>{t("settings.fields.fullDay")}</FieldLabel>
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field>
          <FieldLabel htmlFor={`${id}-opening-start`}>{t("settings.fields.openingStart")}</FieldLabel>
          <Input
            id={`${id}-opening-start`}
            type="time"
            step={60}
            required
            disabled={disabled || fullDay}
            value={typeof openingStart.input === "string" ? openingStart.input : ""}
            ref={openingStart.props.ref}
            onFocus={openingStart.props.onFocus}
            onBlur={openingStart.props.onBlur}
            onChange={(event) => openingStart.onChange(event.currentTarget.value)}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor={`${id}-opening-end`}>{t("settings.fields.openingEnd")}</FieldLabel>
          <Input
            id={`${id}-opening-end`}
            type="time"
            step={60}
            required
            disabled={disabled || fullDay}
            value={fullDay ? "" : typeof openingEnd.input === "string" ? openingEnd.input : ""}
            ref={openingEnd.props.ref}
            onFocus={openingEnd.props.onFocus}
            onBlur={openingEnd.props.onBlur}
            onChange={(event) => openingEnd.onChange(event.currentTarget.value)}
          />
        </Field>
      </div>
      {openingInvalid ? <FieldError>{t("settings.errors.openingHours")}</FieldError> : null}
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
        <Checkbox
          id={`${id}-double-booking`}
          checked={allowDoubleBooking.input === true}
          disabled={disabled}
          inputRef={allowDoubleBooking.props.ref}
          onCheckedChange={(checked) => allowDoubleBooking.onChange(checked)}
        />
        <FieldLabel htmlFor={`${id}-double-booking`}>{t("settings.fields.allowDoubleBooking")}</FieldLabel>
      </Field>
    </fieldset>
  );
}
