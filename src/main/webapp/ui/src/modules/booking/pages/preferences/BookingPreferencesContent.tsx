import { useSuspenseQuery } from "@tanstack/react-query";
import { SaveIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import {
  type BookingDisplayPreferencesDocument,
  bookingDisplayPreferencesQueryOptions,
  browserTimeZone,
  isValidTimeZone,
  minuteOfDay,
  useReplaceBookingDisplayPreferences,
  useResetBookingDisplayPreferences,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import type { BookingDisplayPreferencesControllerReplacement } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferencesControllerReplacement";
import { BookingDisplayPreferencesControllerReplacementSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { Button } from "@/modules/common/ui/button";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { BookingDisplaySettingsFields } from "./BookingDisplaySettingsFields";
import { UserCalendarSubscription } from "./UserCalendarSubscription";

function inputFrom(
  document: Pick<
    BookingDisplayPreferencesDocument,
    "availabilityWindowStart" | "availabilityWindowEnd" | "timezoneMode" | "customTimezone"
  >,
): BookingDisplayPreferencesControllerReplacement {
  return {
    availabilityWindowStart: document.availabilityWindowStart,
    availabilityWindowEnd: document.availabilityWindowEnd,
    timezoneMode: document.timezoneMode,
    customTimezone: document.customTimezone,
  };
}

export function BookingPreferencesContent() {
  const { t } = useTranslation("booking");
  const document = useSuspenseQuery(bookingDisplayPreferencesQueryOptions()).data;
  const [draft, setDraft] = useState<{
    baseline: BookingDisplayPreferencesControllerReplacement;
    input: BookingDisplayPreferencesControllerReplacement;
  }>();
  const input = draft?.input ?? inputFrom(document);
  const replace = useReplaceBookingDisplayPreferences();
  const reset = useResetBookingDisplayPreferences();
  const browserZone = browserTimeZone() ?? document.institutionTimezone;

  const valid =
    v.safeParse(BookingDisplayPreferencesControllerReplacementSchema, input).success &&
    minuteOfDay(input.availabilityWindowStart) < minuteOfDay(input.availabilityWindowEnd) &&
    (input.timezoneMode === "CUSTOM"
      ? Boolean(input.customTimezone && isValidTimeZone(input.customTimezone))
      : input.customTimezone == null);
  const dirty = draft !== undefined;
  const pending = replace.isPending || reset.isPending;

  return (
    <main className="space-y-6 p-4 sm:p-8">
      <DirtyNavigationGuard dirty={dirty} />
      <div>
        <Heading level={3} as="h1" className="mb-2">
          {t("preferences.title")}
        </Heading>
        <p className="text-sm text-muted-foreground">{t("preferences.description")}</p>
      </div>
      <Separator />
      <form
        className="max-w-2xl space-y-6"
        onSubmit={(event) => {
          event.preventDefault();
          if (valid && !pending) replace.mutate(input, { onSuccess: () => setDraft(undefined) });
        }}
      >
        <BookingDisplaySettingsFields
          value={input}
          onChange={(value) => {
            setDraft((current) => {
              const baseline = current?.baseline ?? inputFrom(document);
              return JSON.stringify(value) === JSON.stringify(baseline) ? undefined : { baseline, input: value };
            });
          }}
          browserTimezone={browserZone}
          institutionTimezone={document.institutionTimezone}
          disabled={pending}
        />
        {!valid ? (
          <p role="alert" className="text-sm text-destructive">
            {t("preferences.errors.invalid")}
          </p>
        ) : null}
        {replace.isError || reset.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("preferences.errors.save")}
          </p>
        ) : null}
        {replace.isSuccess && !dirty ? <p role="status">{t("preferences.saved")}</p> : null}
        {reset.isSuccess && !dirty ? <p role="status">{t("preferences.resetComplete")}</p> : null}
        <div className="flex flex-wrap gap-3">
          <Button type="submit" disabled={pending || !dirty || !valid} aria-busy={replace.isPending}>
            <SaveIcon aria-hidden="true" />
            {t("preferences.actions.save")}
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={pending || (!document.overridden && !dirty)}
            aria-busy={reset.isPending}
            onClick={() => reset.mutate(undefined, { onSuccess: () => setDraft(undefined) })}
          >
            {t("preferences.actions.reset")}
          </Button>
        </div>
      </form>
      <Separator />
      <UserCalendarSubscription />
    </main>
  );
}
