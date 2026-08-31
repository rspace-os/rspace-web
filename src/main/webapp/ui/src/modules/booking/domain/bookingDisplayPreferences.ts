import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { bookingApiV2Headers, bookingApiV2JsonHeaders } from "./apiV2";
import { parseApiV2Problem } from "./booking";

export const BookingTimezoneModeSchema = v.picklist(["BROWSER", "INSTITUTION", "CUSTOM"]);
const AvailabilityStartSchema = v.pipe(v.string(), v.regex(/^(?:[01]\d|2[0-3]):[0-5]\d$/));
const AvailabilityEndSchema = v.pipe(v.string(), v.regex(/^(?:(?:[01]\d|2[0-3]):[0-5]\d|24:00)$/));

export const BookingDisplayPreferencesDocumentSchema = v.pipe(
  v.strictObject({
    availabilityWindowStart: AvailabilityStartSchema,
    availabilityWindowEnd: AvailabilityEndSchema,
    timezoneMode: BookingTimezoneModeSchema,
    customTimezone: v.nullable(v.string()),
    institutionTimezone: v.string(),
    overridden: v.boolean(),
  }),
  v.check((document) => minuteOfDay(document.availabilityWindowStart) < minuteOfDay(document.availabilityWindowEnd)),
  v.check((document) =>
    document.timezoneMode === "CUSTOM"
      ? document.customTimezone !== null && isValidTimeZone(document.customTimezone)
      : document.customTimezone === null,
  ),
  v.check((document) => isValidTimeZone(document.institutionTimezone)),
);

export const BookingDisplayPreferencesInputSchema = v.pipe(
  v.strictObject({
    availabilityWindowStart: AvailabilityStartSchema,
    availabilityWindowEnd: AvailabilityEndSchema,
    timezoneMode: BookingTimezoneModeSchema,
    customTimezone: v.nullable(v.string()),
  }),
  v.check((input) => minuteOfDay(input.availabilityWindowStart) < minuteOfDay(input.availabilityWindowEnd)),
  v.check((input) =>
    input.timezoneMode === "CUSTOM"
      ? input.customTimezone !== null && isValidTimeZone(input.customTimezone)
      : input.customTimezone === null,
  ),
);

export type BookingTimezoneMode = v.InferOutput<typeof BookingTimezoneModeSchema>;
export type BookingDisplayPreferencesDocument = v.InferOutput<typeof BookingDisplayPreferencesDocumentSchema>;
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
  institutionTimezone: string;
  overridden: boolean;
};

export const bookingDisplayPreferencesQueryKey = ["api-v2", "users", "me", "booking-preferences"] as const;
const path = "/api/v2/users/me/booking-preferences";

async function requireSuccess(response: Response): Promise<Response> {
  if (!response.ok) throw await parseApiV2Problem(response);
  return response;
}

export async function fetchBookingDisplayPreferences(
  token: string,
  signal?: AbortSignal,
): Promise<BookingDisplayPreferencesDocument> {
  const response = await requireSuccess(await fetch(path, { headers: bookingApiV2Headers(token), signal }));
  return parseOrThrow(BookingDisplayPreferencesDocumentSchema, (await response.json()) as unknown);
}

export async function replaceBookingDisplayPreferences(
  input: BookingDisplayPreferencesInput,
  token: string,
): Promise<BookingDisplayPreferencesDocument> {
  const parsed = v.parse(BookingDisplayPreferencesInputSchema, input);
  const response = await requireSuccess(
    await fetch(path, {
      method: "PUT",
      headers: bookingApiV2JsonHeaders(token),
      body: JSON.stringify(parsed),
    }),
  );
  return parseOrThrow(BookingDisplayPreferencesDocumentSchema, (await response.json()) as unknown);
}

export async function resetBookingDisplayPreferences(token: string): Promise<void> {
  const response = await fetch(path, { method: "DELETE", headers: bookingApiV2Headers(token) });
  if (response.status !== 204) throw await parseApiV2Problem(response);
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
    institutionTimezone: document.institutionTimezone,
    overridden: document.overridden,
  };
}

export function useBookingDisplayPreferences(): ResolvedBookingDisplayPreferences {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const document = useSuspenseQuery({
    queryKey: bookingDisplayPreferencesQueryKey,
    queryFn: ({ signal }) => fetchBookingDisplayPreferences(token, signal),
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
