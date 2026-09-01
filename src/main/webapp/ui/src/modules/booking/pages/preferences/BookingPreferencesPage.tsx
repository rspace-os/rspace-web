import { useSuspenseQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import {
  type BookingDisplayPreferencesInput,
  BookingDisplayPreferencesInputSchema,
  bookingDisplayPreferencesQueryKey,
  browserTimeZone,
  fetchBookingDisplayPreferences,
  useReplaceBookingDisplayPreferences,
  useResetBookingDisplayPreferences,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { Button } from "@/modules/common/ui/button";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { BookingDisplaySettingsFields } from "./BookingDisplaySettingsFields";
import { UserCalendarSubscription } from "./UserCalendarSubscription";

function inputFrom(document: {
  availabilityWindowStart: string;
  availabilityWindowEnd: string;
  timezoneMode: BookingDisplayPreferencesInput["timezoneMode"];
  customTimezone: string | null;
}): BookingDisplayPreferencesInput {
  return {
    availabilityWindowStart: document.availabilityWindowStart,
    availabilityWindowEnd: document.availabilityWindowEnd,
    timezoneMode: document.timezoneMode,
    customTimezone: document.customTimezone,
  };
}

export default function BookingPreferencesPage() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const document = useSuspenseQuery({
    queryKey: bookingDisplayPreferencesQueryKey,
    queryFn: ({ signal }) => fetchBookingDisplayPreferences(token, signal),
  }).data;
  const [input, setInput] = useState(() => inputFrom(document));
  const replace = useReplaceBookingDisplayPreferences();
  const reset = useResetBookingDisplayPreferences();
  const browserZone = browserTimeZone() ?? document.institutionTimezone;

  useEffect(() => setInput(inputFrom(document)), [document]);

  const valid = v.safeParse(BookingDisplayPreferencesInputSchema, input).success;
  const dirty = JSON.stringify(input) !== JSON.stringify(inputFrom(document));
  const pending = replace.isPending || reset.isPending;

  return (
    <main className="space-y-6 p-4 sm:p-8">
      <DirtyNavigationGuard dirty={dirty} />
      <div>
        <Heading level={2} as="h1" className="mb-2">
          {t("preferences.title")}
        </Heading>
        <p className="text-sm text-muted-foreground">{t("preferences.description")}</p>
      </div>
      <Separator />
      <form
        className="max-w-2xl space-y-6"
        onSubmit={(event) => {
          event.preventDefault();
          if (valid) replace.mutate(input);
        }}
      >
        <BookingDisplaySettingsFields
          value={input}
          onChange={setInput}
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
            {t("preferences.actions.save")}
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={pending || (!document.overridden && !dirty)}
            aria-busy={reset.isPending}
            onClick={() => reset.mutate()}
          >
            {t("preferences.actions.reset")}
          </Button>
        </div>
      </form>
      <Separator />
      <UserCalendarSubscription token={token} />
    </main>
  );
}
