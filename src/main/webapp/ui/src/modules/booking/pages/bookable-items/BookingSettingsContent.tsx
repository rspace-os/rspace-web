import { Form, isDirty, reset, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { isEqual, pick } from "es-toolkit";
import { CheckIcon } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import {
  type BookingAdminSettings,
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
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import { Button } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { BookingDisplaySettingsFields } from "../preferences/BookingDisplaySettingsFields";

function displayInput(settings: BookingAdminSettings): BookingDisplayPreferencesInput {
  return pick(settings, [
    "availabilityWindowStart",
    "availabilityWindowEnd",
    "timezoneMode",
    "customTimezone",
    "timeFormat",
  ]);
}

export function BookingSettingsContent() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const settingsQuery = useSuspenseQuery({
    queryKey: ["api-v2", "booking-settings", "admin"],
    queryFn: ({ signal }) => loadBookingAdminSettings(token, signal),
    // A 403 means the caller is not a sysadmin; retrying cannot change that.
    retry: retryUnlessClientError,
  });
  const loadedSettings = settingsQuery.data;
  const [settings, setSettings] = useState(loadedSettings);
  const [displaySettings, setDisplaySettings] = useState(() => displayInput(settings));
  const form = useForm({ schema: SchedulingSettingsSchema, initialInput: settings });
  const [reloading, setReloading] = useState(false);
  // Each adopted baseline starts a new editing session: remounting the fields drops closed-day drafts and the
  // opening hours that Open all day would restore, for saves, accepted refreshes and reloads alike.
  const [editorVersion, setEditorVersion] = useState(0);
  const adoptSettings = useCallback(
    (next: BookingAdminSettings) => {
      setSettings(next);
      reset(form, { initialInput: next });
      setDisplaySettings(displayInput(next));
      setEditorVersion((version) => version + 1);
    },
    [form],
  );
  const mutation = useMutation({
    mutationFn: (input: SchedulingSettings) =>
      saveBookingSettings(
        {
          ...input,
          ...displaySettings,
        } as BookingSettingsInput,
        settings.etag,
        token,
      ),
    onSuccess: async (saved) => {
      queryClient.setQueryData(["api-v2", "booking-settings", "admin"], saved);
      adoptSettings(saved);
      await queryClient.invalidateQueries({ queryKey: bookingDisplayPreferencesQueryKey });
    },
  });
  const displaySettingsValid = v.safeParse(BookingDisplayPreferencesInputSchema, displaySettings).success;
  const dirty = isDirty(form) || !isEqual(displaySettings, displayInput(settings));

  // Refresh the server baseline only when no local edits (including weekday hours) would be lost.
  useEffect(() => {
    if (loadedSettings !== settings && !dirty && !mutation.isPending) {
      adoptSettings(loadedSettings);
    }
  }, [loadedSettings, settings, dirty, mutation.isPending, adoptSettings]);

  const stale =
    mutation.error instanceof ApiV2ProblemError && mutation.error.status === 412;

  const saved = mutation.isSuccess && !dirty;
  const pending = mutation.isPending || reloading;

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
        onSubmit={(input) => (pending ? undefined : mutation.mutateAsync(input))}
      >
        <SchedulingSettingsFields key={editorVersion} form={form} disabled={pending} />
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
            disabled={pending}
          />
        </section>
        {mutation.isError ? (
          <FieldError>{stale ? t("settings.errors.stale") : t("settings.errors.save")}</FieldError>
        ) : null}
        {stale ? (
          <Button
            type="button"
            variant="outline"
            disabled={pending || settingsQuery.isFetching}
            onClick={async () => {
              setReloading(true);
              try {
                const latest = await settingsQuery.refetch();
                if (latest.isSuccess) {
                  adoptSettings(latest.data);
                  mutation.reset();
                }
              } finally {
                setReloading(false);
              }
            }}
          >
            {t("settings.actions.reload")}
          </Button>        ) : null}
        <Button
          type="submit"
          disabled={pending || !dirty || !displaySettingsValid}
          aria-busy={pending}
          className={
            saved
              ? "bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-100 dark:bg-emerald-500 dark:hover:bg-emerald-400"
              : undefined
          }
        >
          {saved ? <CheckIcon aria-hidden="true" /> : null}
          {saved ? t("preferences.actions.saved") : t("settings.actions.save")}
        </Button>
        {/* The disabled button's new label is not announced, so the save is confirmed here too. */}
        <p role="status" className="sr-only">
          {saved ? t("preferences.actions.saved") : null}
        </p>
      </Form>
    </main>
  );
}
