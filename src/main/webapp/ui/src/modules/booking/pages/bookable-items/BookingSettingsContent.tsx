import { Form, isDirty, reset, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { CheckIcon, SaveIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import {
  type SchedulingSettings,
  SchedulingSettingsFields,
  SchedulingSettingsSchema,
  saveBookingSettings,
} from "@/modules/booking/configuration/schedulingSettings";
import { bookingAdminSettingsQuery } from "@/modules/booking/configuration/schedulingSettingsApi";
import {
  browserTimeZone,
  hasValidAvailabilityWindow,
  hasValidCustomTimezone,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import {
  invalidateGetAdminBookingSettings,
  invalidateGetBookingSettings,
} from "@/modules/common/api/v2/generated/booking-settings/booking-settings";
import type { BookingDisplayPreferencesControllerReplacement } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferencesControllerReplacement";
import { invalidateGetMyBookingPreferences } from "@/modules/common/api/v2/generated/users/users";
import { BookingDisplayPreferencesControllerReplacementSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { Button } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { BookingDisplaySettingsFields } from "../preferences/BookingDisplaySettingsFields";

export function BookingSettingsContent() {
  const { t } = useTranslation("booking");

  const queryClient = useQueryClient();
  const loadedSettings = useSuspenseQuery(bookingAdminSettingsQuery()).data;
  const [settings, setSettings] = useState(loadedSettings);
  const [editorVersion, setEditorVersion] = useState(0);
  const [displaySettings, setDisplaySettings] = useState<BookingDisplayPreferencesControllerReplacement>({
    availabilityWindowStart: settings.availabilityWindowStart,
    availabilityWindowEnd: settings.availabilityWindowEnd,
    timezoneMode: settings.timezoneMode,
    customTimezone: settings.customTimezone,
    timeFormat: settings.timeFormat ?? "AUTOMATIC",
  });
  const form = useForm({ schema: SchedulingSettingsSchema, initialInput: settings });
  const mutation = useMutation({
    mutationFn: (input: SchedulingSettings) =>
      saveBookingSettings(
        {
          ...input,
          ...displaySettings,
        },
        settings.etag,
      ),
    onSuccess: async (saved) => {
      setSettings(saved);
      await invalidateGetAdminBookingSettings(queryClient);
      await invalidateGetBookingSettings(queryClient);
      reset(form, { initialInput: saved });
      setEditorVersion((version) => version + 1);
      setDisplaySettings({
        availabilityWindowStart: saved.availabilityWindowStart,
        availabilityWindowEnd: saved.availabilityWindowEnd,
        timezoneMode: saved.timezoneMode,
        customTimezone: saved.customTimezone,
        timeFormat: saved.timeFormat ?? "AUTOMATIC",
      });
      await invalidateGetMyBookingPreferences(queryClient);
    },
  });
  const preferenceValues = { ...displaySettings, customTimezone: displaySettings.customTimezone ?? null };
  const displaySettingsValid =
    v.safeParse(BookingDisplayPreferencesControllerReplacementSchema, displaySettings).success &&
    hasValidAvailabilityWindow(preferenceValues) &&
    hasValidCustomTimezone(preferenceValues);
  const dirty =
    isDirty(form) ||
    JSON.stringify(displaySettings) !==
      JSON.stringify({
        availabilityWindowStart: settings.availabilityWindowStart,
        availabilityWindowEnd: settings.availabilityWindowEnd,
        timezoneMode: settings.timezoneMode,
        customTimezone: settings.customTimezone,
        timeFormat: settings.timeFormat ?? "AUTOMATIC",
      });

  const saved = mutation.isSuccess && !dirty;

  return (
    <main className="p-4 sm:p-8">
      <DirtyNavigationGuard dirty={dirty} />
      <Heading level={3} as="h1" className="mb-2">
        {t("settings.title")}
      </Heading>
      <p className="mb-5 text-sm text-muted-foreground">{t("settings.description")}</p>
      <Separator className="mb-8 h-px bg-gray-300" />
      <Form
        of={form}
        className="max-w-2xl space-y-8"
        onChange={() => mutation.reset()}
        onSubmit={(input) => {
          if (!displaySettingsValid) return;
          return mutation.mutateAsync(input);
        }}
      >
        <SchedulingSettingsFields key={editorVersion} form={form} disabled={mutation.isPending} />
        <Separator />
        <section className="space-y-4" aria-labelledby="booking-display-defaults-heading">
          <div>
            <Heading level={3} as="h2" id="booking-display-defaults-heading">
              {t("settings.displayDefaults.title")}
            </Heading>
            <p className="text-sm text-muted-foreground">{t("settings.displayDefaults.description")}</p>
          </div>
          <BookingDisplaySettingsFields
            value={{ ...displaySettings, customTimezone: displaySettings.customTimezone ?? null }}
            onChange={setDisplaySettings}
            browserTimezone={browserTimeZone() ?? settings.institutionTimezone}
            institutionTimezone={settings.institutionTimezone}
            disabled={mutation.isPending}
          />
        </section>
        {mutation.isError ? (
          <FieldError>
            {t(
              mutation.error instanceof ApiV2ProblemError && mutation.error.status === 412
                ? "settings.errors.stale"
                : "settings.errors.save",
            )}
          </FieldError>
        ) : null}
        <Button
          type="submit"
          disabled={mutation.isPending || !dirty || !displaySettingsValid}
          aria-busy={mutation.isPending}
          className={
            saved
              ? "bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-100 dark:bg-emerald-500 dark:hover:bg-emerald-400"
              : undefined
          }
        >
          {saved ? <CheckIcon aria-hidden="true" /> : <SaveIcon aria-hidden="true" />}
          {t(saved ? "preferences.actions.saved" : "settings.actions.save")}
        </Button>
      </Form>
    </main>
  );
}
