import { Form, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import {
  type BookingDisplayPreferencesInput,
  BookingDisplayPreferencesInputSchema,
  bookingDisplayPreferencesQueryKey,
  browserTimeZone,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { BookingDisplaySettingsFields } from "../preferences/BookingDisplaySettingsFields";
import {
  type BookingSettingsInput,
  loadBookingSettings,
  type SchedulingSettings,
  SchedulingSettingsFields,
  SchedulingSettingsSchema,
  saveBookingSettings,
} from "./schedulingSettings";

export default function BookingSettingsPage() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const settings = useSuspenseQuery({
    queryKey: ["api-v2", "booking-settings"],
    queryFn: ({ signal }) => loadBookingSettings(token, signal),
  }).data;
  const [displaySettings, setDisplaySettings] = useState<BookingDisplayPreferencesInput>({
    availabilityWindowStart: settings.availabilityWindowStart,
    availabilityWindowEnd: settings.availabilityWindowEnd,
    timezoneMode: settings.timezoneMode,
    customTimezone: settings.customTimezone,
  });
  const form = useForm({ schema: SchedulingSettingsSchema, initialInput: settings });
  const mutation = useMutation({
    mutationFn: (input: SchedulingSettings) =>
      saveBookingSettings(
        { ...input, ...displaySettings } as BookingSettingsInput,
        settings.configurationVersion,
        token,
      ),
    onSuccess: async (saved) => {
      queryClient.setQueryData(["api-v2", "booking-settings"], saved);
      await queryClient.invalidateQueries({ queryKey: bookingDisplayPreferencesQueryKey });
    },
  });
  const displaySettingsValid = v.safeParse(BookingDisplayPreferencesInputSchema, displaySettings).success;

  return (
    <main className="p-4 sm:p-8">
      <Heading level={2} as="h1" className="mb-2">
        {t("settings.title")}
      </Heading>
      <p className="mb-5 text-sm text-muted-foreground">{t("settings.description")}</p>
      <Separator className="mb-8 h-px bg-gray-300" />
      <Form of={form} className="max-w-2xl space-y-8" onSubmit={(input) => mutation.mutateAsync(input)}>
        <SchedulingSettingsFields form={form} disabled={mutation.isPending} />
        <Separator />
        <section className="space-y-4" aria-labelledby="booking-display-defaults-heading">
          <div>
            <Heading level={3} as="h2" id="booking-display-defaults-heading">
              {t("settings.displayDefaults.title")}
            </Heading>
            <p className="text-sm text-muted-foreground">{t("settings.displayDefaults.description")}</p>
          </div>
          <BookingDisplaySettingsFields
            value={displaySettings}
            onChange={setDisplaySettings}
            browserTimezone={browserTimeZone() ?? settings.institutionTimezone}
            institutionTimezone={settings.institutionTimezone}
            disabled={mutation.isPending}
          />
        </section>
        {!displaySettingsValid ? <FieldError>{t("preferences.errors.invalid")}</FieldError> : null}
        {mutation.isError ? (
          <FieldError>
            {t(
              mutation.error instanceof ApiV2ProblemError &&
                mutation.error.code === "errors.api.v2.bookingConfiguration.stale"
                ? "settings.errors.stale"
                : "settings.errors.save",
            )}
          </FieldError>
        ) : null}
        {mutation.isSuccess ? <p role="status">{t("settings.saved")}</p> : null}
        <Button type="submit" disabled={mutation.isPending || !displaySettingsValid} aria-busy={mutation.isPending}>
          {t("settings.actions.save")}
        </Button>
      </Form>
    </main>
  );
}
