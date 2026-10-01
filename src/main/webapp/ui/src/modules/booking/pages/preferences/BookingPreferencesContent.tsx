import { useSuspenseQuery } from "@tanstack/react-query";
import { CheckIcon } from "lucide-react";
import { useState } from "react";
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
import { BookingNotificationPreferencesSection } from "./BookingNotificationPreferencesSection";
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

export function BookingPreferencesContent() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const document = useSuspenseQuery({
    queryKey: bookingDisplayPreferencesQueryKey,
    queryFn: ({ signal }) => fetchBookingDisplayPreferences(token, signal),
  }).data;
  const [draft, setDraft] = useState<BookingDisplayPreferencesInput>();
  const input = draft ?? inputFrom(document);
  const replace = useReplaceBookingDisplayPreferences();
  const reset = useResetBookingDisplayPreferences();
  const browserZone = browserTimeZone() ?? document.institutionTimezone;

  const valid = v.safeParse(BookingDisplayPreferencesInputSchema, input).success;
  const dirty = JSON.stringify(input) !== JSON.stringify(inputFrom(document));
  const pending = replace.isPending || reset.isPending;
  const saved = replace.isSuccess && !dirty;

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
          if (valid && dirty) {
            reset.reset();
            replace.mutate(input, { onSuccess: () => setDraft(undefined) });
          }
        }}
      >
        <BookingDisplaySettingsFields
          value={input}
          onChange={(value) => {
            replace.reset();
            reset.reset();
            setDraft(value);
          }}
          browserTimezone={browserZone}
          institutionTimezone={document.institutionTimezone}
          disabled={pending}
        />
        {replace.isError || reset.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("preferences.errors.save")}
          </p>
        ) : null}
        {reset.isSuccess && !dirty ? <p role="status">{t("preferences.resetComplete")}</p> : null}
        <div className="flex flex-wrap gap-3">
          <Button
            type="submit"
            disabled={pending || !dirty || !valid}
            aria-busy={replace.isPending}
            className={
              saved
                ? "bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-100 dark:bg-emerald-500 dark:hover:bg-emerald-400"
                : undefined
            }
          >
            {saved ? <CheckIcon aria-hidden="true" /> : null}
            {t(saved ? "preferences.actions.saved" : "preferences.actions.save")}
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={pending || (!document.overridden && !dirty)}
            aria-busy={reset.isPending}
            onClick={() => {
              replace.reset();
              reset.mutate(undefined, { onSuccess: () => setDraft(undefined) });
            }}
          >
            {t("preferences.actions.reset")}
          </Button>
        </div>
      </form>
      <Separator />
      <BookingNotificationPreferencesSection />
      <Separator />
      <UserCalendarSubscription token={token} />
    </main>
  );
}
