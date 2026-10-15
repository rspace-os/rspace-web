import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { createContext, createElement, type ReactNode, useContext } from "react";
import * as v from "valibot";
import { apiV2Headers, apiV2JsonHeaders } from "@/modules/common/api/apiV2Headers";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { parseApiV2Problem, requireSuccess } from "./booking";
import { minuteOfWallTime, OpeningHoursEndSchema, OpeningHoursStartSchema } from "./bookingOpeningHours";
import type { BookingTimeFormat } from "./bookingTime";

export const BookingTimezoneModeSchema = v.picklist(["BROWSER", "INSTITUTION", "CUSTOM"]);
export type BookingTimezoneMode = v.InferOutput<typeof BookingTimezoneModeSchema>;

/** Automatic follows the browser region; H12 and H24 force a 12- or 24-hour clock. Omitted means Automatic. */
export const BookingTimeFormatSchema = v.picklist(["AUTOMATIC", "H12", "H24"] satisfies BookingTimeFormat[]);

const BookingDisplayPreferencesEntries = {
  availabilityWindowStart: OpeningHoursStartSchema,
  availabilityWindowEnd: OpeningHoursEndSchema,
  timezoneMode: BookingTimezoneModeSchema,
  customTimezone: v.nullable(v.string()),
};

type BookingDisplayPreferenceValues = {
  availabilityWindowStart: string;
  availabilityWindowEnd: string;
  timezoneMode: BookingTimezoneMode;
  customTimezone: string | null;
  timeFormat?: BookingTimeFormat;
};

const hasValidAvailabilityWindow = ({
  availabilityWindowStart,
  availabilityWindowEnd,
}: BookingDisplayPreferenceValues) => minuteOfDay(availabilityWindowStart) < minuteOfDay(availabilityWindowEnd);

const hasValidCustomTimezone = ({ timezoneMode, customTimezone }: BookingDisplayPreferenceValues) =>
  timezoneMode === "CUSTOM" ? customTimezone !== null && isValidTimeZone(customTimezone) : customTimezone === null;

export const BookingDisplayPreferencesDocumentSchema = v.pipe(
  v.strictObject({
    ...BookingDisplayPreferencesEntries,
    timeFormat: v.optional(BookingTimeFormatSchema, "AUTOMATIC"),
    institutionTimezone: v.string(),
    overridden: v.boolean(),
  }),
  v.check((document) => hasValidAvailabilityWindow(document)),
  v.check((document) => hasValidCustomTimezone(document)),
  v.check((document) => isValidTimeZone(document.institutionTimezone)),
);

export const BookingDisplayPreferencesInputSchema = v.pipe(
  v.strictObject({
    ...BookingDisplayPreferencesEntries,
    timeFormat: v.optional(BookingTimeFormatSchema),
  }),
  v.forward(
    v.check((document) => hasValidAvailabilityWindow(document)),
    ["availabilityWindowEnd"],
  ),
  v.forward(
    v.check((document) => hasValidCustomTimezone(document)),
    ["customTimezone"],
  ),
);

export type BookingDisplayPreferencesDocument = v.InferInput<typeof BookingDisplayPreferencesDocumentSchema>;
export type BookingDisplayPreferencesInput = v.InferOutput<typeof BookingDisplayPreferencesInputSchema>;

export type ResolvedBookingDisplayPreferences = {
  availabilityWindow: {
    start: string;
    end: string;
    startMinute: number;
    endMinute: number;
  };
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

export const bookingDisplayPreferencesQueryKey = ["api-v2", "users", "me", "booking-preferences"] as const;
const path = "/api/v2/users/me/booking-preferences";

export async function fetchBookingDisplayPreferences(
  token: string,
  signal?: AbortSignal,
): Promise<BookingDisplayPreferencesDocument> {
  const response = await requireSuccess(await fetch(path, { headers: apiV2Headers(token), signal }));
  return parseOrThrow(BookingDisplayPreferencesDocumentSchema, (await response.json()) as unknown);
}

/** Query options for callers that need the display preferences document directly. */
export function bookingDisplayPreferencesQueryOptions(token: string) {
  return {
    queryKey: bookingDisplayPreferencesQueryKey,
    queryFn: ({ signal }: { signal: AbortSignal }) => fetchBookingDisplayPreferences(token, signal),
  } as const;
}

export async function replaceBookingDisplayPreferences(
  input: BookingDisplayPreferencesInput,
  token: string,
): Promise<BookingDisplayPreferencesDocument> {
  const parsed = v.parse(BookingDisplayPreferencesInputSchema, input);
  const response = await requireSuccess(
    await fetch(path, {
      method: "PUT",
      headers: apiV2JsonHeaders(token),
      body: JSON.stringify(parsed),
    }),
  );
  return parseOrThrow(BookingDisplayPreferencesDocumentSchema, (await response.json()) as unknown);
}

export async function resetBookingDisplayPreferences(token: string): Promise<void> {
  const response = await fetch(path, { method: "DELETE", headers: apiV2Headers(token) });
  if (response.status !== 204) throw await parseApiV2Problem(response);
}

export function minuteOfDay(value: string): number {
  return minuteOfWallTime(value);
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
    timeFormat: document.timeFormat ?? "AUTOMATIC",
    institutionTimezone: document.institutionTimezone,
    overridden: document.overridden,
  };
}

export function useBookingDisplayPreferences(): ResolvedBookingDisplayPreferences {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const document = useSuspenseQuery({
    queryKey: bookingDisplayPreferencesQueryKey,
    queryFn: ({ signal }) => fetchBookingDisplayPreferences(token, signal),
    staleTime: Infinity,
  }).data;
  return resolveBookingDisplayPreferences(document);
}

export function useReplaceBookingDisplayPreferences() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: BookingDisplayPreferencesInput) => replaceBookingDisplayPreferences(input, token),
    onSuccess: (document) => queryClient.setQueryData(bookingDisplayPreferencesQueryKey, document),
  });
}

export function useResetBookingDisplayPreferences() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await resetBookingDisplayPreferences(token);
      return fetchBookingDisplayPreferences(token);
    },
    onSuccess: (document) => queryClient.setQueryData(bookingDisplayPreferencesQueryKey, document),
  });
}

export function todayInTimeZone(timeZone: string, now = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

export function bookingTimeZoneOptions(...required: Array<string | null | undefined>): string[] {
  const supported = typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("timeZone") : [];
  return [...new Set([...supported, ...required.filter((value): value is string => Boolean(value)), "UTC"])].sort(
    (left, right) => left.localeCompare(right),
  );
}
