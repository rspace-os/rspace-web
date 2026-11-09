import { Temporal } from "@js-temporal/polyfill";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { uniq } from "es-toolkit";
import { createContext, createElement, type ReactNode, useContext } from "react";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import type { BookingDisplayPreferences } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferences";
import type { BookingDisplayPreferencesReplacement } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferencesReplacement";
import type { BookingTimezoneMode } from "@/modules/common/api/v2/generated/models/bookingTimezoneMode";
import {
  getGetMyBookingPreferencesQueryKey,
  getGetMyBookingPreferencesSuspenseQueryOptions,
  getMyBookingPreferences,
  replaceMyBookingPreferences,
  resetMyBookingPreferences,
} from "@/modules/common/api/v2/generated/users/users";
import { validOpeningHours } from "./bookingOpeningHours";
import type { BookingTimeFormat } from "./bookingTime";

export type BookingDisplayPreferencesDocument = BookingDisplayPreferences;
export type ResolvedBookingDisplayPreferences = {
  availabilityWindow: { start: string; end: string; startMinute: number; endMinute: number };
  timeZone: string;
  timezoneMode: BookingTimezoneMode;
  timeFormat: BookingTimeFormat;
  institutionTimezone: string;
  overridden: boolean;
};

const BookingTimeFormatContext = createContext<BookingTimeFormat>("AUTOMATIC");

/** The current route's display choice; standalone components use Automatic when no provider is present. */
export function useBookingTimeFormat(): BookingTimeFormat {
  return useContext(BookingTimeFormatContext);
}

export function BookingTimeFormatProvider({
  timeFormat,
  children,
}: {
  timeFormat: BookingTimeFormat;
  children: ReactNode;
}) {
  return createElement(BookingTimeFormatContext.Provider, { value: timeFormat }, children);
}

export function hasValidAvailabilityWindow({
  availabilityWindowStart,
  availabilityWindowEnd,
}: Pick<BookingDisplayPreferencesReplacement, "availabilityWindowStart" | "availabilityWindowEnd">): boolean {
  return minuteOfDay(availabilityWindowStart) < minuteOfDay(availabilityWindowEnd);
}

export function hasValidCustomTimezone({
  timezoneMode,
  customTimezone,
}: Pick<BookingDisplayPreferencesReplacement, "timezoneMode" | "customTimezone">): boolean {
  return timezoneMode === "CUSTOM"
    ? typeof customTimezone === "string" && isValidTimeZone(customTimezone)
    : customTimezone === null;
}

export function checkBookingDisplayPreferences(
  input: Pick<
    BookingDisplayPreferencesReplacement,
    "availabilityWindowStart" | "availabilityWindowEnd" | "timezoneMode" | "customTimezone"
  >,
): void {
  if (
    !hasValidAvailabilityWindow(input) ||
    !validOpeningHours(input.availabilityWindowStart, input.availabilityWindowEnd)
  )
    throw new Error("Invalid availability window");
  if (!hasValidCustomTimezone({ ...input, customTimezone: input.customTimezone ?? null }))
    throw new Error("Invalid custom timezone");
}

export function mapBookingDisplayPreferences({
  availabilityWindowStart,
  availabilityWindowEnd,
  timezoneMode,
  customTimezone,
  timeFormat,
  institutionTimezone,
  overridden,
}: BookingDisplayPreferences): BookingDisplayPreferencesDocument {
  const document = {
    availabilityWindowStart,
    availabilityWindowEnd,
    timezoneMode,
    customTimezone,
    timeFormat,
    institutionTimezone,
    overridden,
  };
  checkBookingDisplayPreferences(document);
  if (!isValidTimeZone(document.institutionTimezone)) throw new Error("Invalid institution timezone");
  return document;
}

export function bookingDisplayPreferencesQueryOptions() {
  return getGetMyBookingPreferencesSuspenseQueryOptions<BookingDisplayPreferencesDocument>({
    query: { select: mapBookingDisplayPreferences, staleTime: Infinity },
  });
}

export async function fetchBookingDisplayPreferences(signal?: AbortSignal): Promise<BookingDisplayPreferencesDocument> {
  return mapBookingDisplayPreferences(await getMyBookingPreferences({ signal }));
}
export async function replaceBookingDisplayPreferences(
  input: BookingDisplayPreferencesReplacement,
): Promise<BookingDisplayPreferencesDocument> {
  checkBookingDisplayPreferences(input);
  return mapBookingDisplayPreferences(await replaceMyBookingPreferences(input));
}
export async function resetBookingDisplayPreferences(): Promise<void> {
  await resetMyBookingPreferences();
}

export function minuteOfDay(value: string): number {
  if (value === "24:00") return 24 * 60;
  const [hour, minute] = value.split(":").map(Number);
  return hour * 60 + minute;
}

export function isValidTimeZone(value: string): boolean {
  if (!value) return false;
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: value }).format();
    return true;
  } catch {
    return false;
  }
}

export function browserTimeZone(): string | null {
  const value = Intl.DateTimeFormat().resolvedOptions().timeZone;
  return value && isValidTimeZone(value) ? value : null;
}

export function resolveBookingDisplayPreferences(
  document: BookingDisplayPreferencesDocument,
  detectedBrowserTimeZone: string | null = browserTimeZone(),
): ResolvedBookingDisplayPreferences {
  const timeZone =
    document.timezoneMode === "CUSTOM"
      ? (document.customTimezone ?? document.institutionTimezone)
      : document.timezoneMode === "INSTITUTION"
        ? document.institutionTimezone
        : detectedBrowserTimeZone && isValidTimeZone(detectedBrowserTimeZone)
          ? detectedBrowserTimeZone
          : document.institutionTimezone;
  return {
    availabilityWindow: {
      start: document.availabilityWindowStart,
      end: document.availabilityWindowEnd,
      startMinute: minuteOfDay(document.availabilityWindowStart),
      endMinute: minuteOfDay(document.availabilityWindowEnd),
    },
    timeZone,
    timezoneMode: document.timezoneMode,
    timeFormat: document.timeFormat,
    institutionTimezone: document.institutionTimezone,
    overridden: document.overridden,
  };
}

export function useBookingDisplayPreferences(): ResolvedBookingDisplayPreferences {
  return resolveBookingDisplayPreferences(
    useSuspenseQuery(
      getGetMyBookingPreferencesSuspenseQueryOptions<BookingDisplayPreferencesDocument>({
        query: { select: mapBookingDisplayPreferences, staleTime: Infinity },
      }),
    ).data,
  );
}

export function useReplaceBookingDisplayPreferences() {
  const options = useQueryClient();
  return useMutation<BookingDisplayPreferencesDocument, ErrorType<unknown>, BookingDisplayPreferencesReplacement>({
    mutationFn: replaceBookingDisplayPreferences,
    onSuccess: (data) => options.setQueryData(getGetMyBookingPreferencesQueryKey(), data),
  });
}

export function useResetBookingDisplayPreferences() {
  const options = useQueryClient();
  return useMutation<BookingDisplayPreferencesDocument, ErrorType<unknown>, void>({
    mutationFn: async () => {
      await resetBookingDisplayPreferences();
      return fetchBookingDisplayPreferences();
    },
    onSuccess: (data) => options.setQueryData(getGetMyBookingPreferencesQueryKey(), data),
  });
}
export function todayInTimeZone(timeZone: string, now = new Date()): string {
  return Temporal.Instant.fromEpochMilliseconds(now.getTime()).toZonedDateTimeISO(timeZone).toPlainDate().toString();
}

export function bookingTimeZoneOptions(...required: Array<string | null | undefined>): string[] {
  const supported = typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("timeZone") : [];
  return uniq([...supported, ...required.filter((value): value is string => Boolean(value)), "UTC"]).sort(
    (left, right) => left.localeCompare(right),
  );
}
