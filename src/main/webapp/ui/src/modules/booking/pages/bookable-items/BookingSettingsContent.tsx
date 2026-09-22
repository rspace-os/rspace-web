import { Form, isDirty, reset, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import {
  type BookingSettingsInput,
  loadBookingAdminSettings,
  type SchedulingSettings,
  SchedulingSettingsFields,
  SchedulingSettingsSchema,
  saveBookingSettings,
} from "@/modules/booking/configuration/schedulingSettings";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import {
  type BookingDisplayPreferencesInput,
  BookingDisplayPreferencesInputSchema,
  bookingDisplayPreferencesQueryKey,
  browserTimeZone,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { Button } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { BookingDisplaySettingsFields } from "../preferences/BookingDisplaySettingsFields";

export function BookingSettingsContent() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const loadedSettings = useSuspenseQuery({
    queryKey: ["api-v2", "booking-settings", "admin"],
    queryFn: ({ signal }) => loadBookingAdminSettings(token, signal),
  }).data;
  const [settings, setSettings] = useState(loadedSettings);
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
        {
          ...input,
          ...displaySettings,
        } as BookingSettingsInput,
        settings.configurationVersion,
        token,
      ),
    onSuccess: async (saved) => {
      setSettings(saved);
      queryClient.setQueryData(["api-v2", "booking-settings", "admin"], saved);
      reset(form, { initialInput: saved });
      setDisplaySettings({
        availabilityWindowStart: saved.availabilityWindowStart,
        availabilityWindowEnd: saved.availabilityWindowEnd,
        timezoneMode: saved.timezoneMode,
        customTimezone: saved.customTimezone,
      });
      await queryClient.invalidateQueries({ queryKey: bookingDisplayPreferencesQueryKey });
    },
  });
  const displaySettingsValid = v.safeParse(BookingDisplayPreferencesInputSchema, displaySettings).success;
  const dirty =
    isDirty(form) ||
    JSON.stringify(displaySettings) !==
      JSON.stringify({
        availabilityWindowStart: settings.availabilityWindowStart,
        availabilityWindowEnd: settings.availabilityWindowEnd,
        timezoneMode: settings.timezoneMode,
        customTimezone: settings.customTimezone,
      });

  return (
    <main className="p-4 sm:p-8">
      <DirtyNavigationGuard dirty={dirty} />
      <Heading level={3} as="h1" className="mb-2">
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
        {mutation.isSuccess && !dirty ? <p role="status">{t("settings.saved")}</p> : null}
        <Button
          type="submit"
          disabled={mutation.isPending || !dirty || !displaySettingsValid}
          aria-busy={mutation.isPending}
        >
          {t("settings.actions.save")}
        </Button>
      </Form>
    </main>
  );
}
