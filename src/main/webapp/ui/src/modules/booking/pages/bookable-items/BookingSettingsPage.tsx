import { Form, isDirty, reset, useForm } from "@formisch/react";
import { useMutation, useQuery, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
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
import { searchBookingSettingsGrantees } from "@/modules/common/resource-access/resourceAccess";
import type { ResourceGranteeDirectoryEntry } from "@/modules/common/resource-access/schemas";
import { Button } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { BookingDisplaySettingsFields } from "../preferences/BookingDisplaySettingsFields";

export default function BookingSettingsPage() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const settings = useSuspenseQuery({
    queryKey: ["api-v2", "booking-settings", "admin"],
    queryFn: ({ signal }) => loadBookingAdminSettings(token, signal),
  }).data;
  const [displaySettings, setDisplaySettings] = useState<BookingDisplayPreferencesInput>({
    availabilityWindowStart: settings.availabilityWindowStart,
    availabilityWindowEnd: settings.availabilityWindowEnd,
    timezoneMode: settings.timezoneMode,
    customTimezone: settings.customTimezone,
  });
  const [defaultSharedWith, setDefaultSharedWith] = useState(settings.defaultSharedWith);
  const [selectedGrantees, setSelectedGrantees] = useState<readonly ResourceGranteeDirectoryEntry[]>(
    settings.selectedAccessGrantees,
  );
  const [granteeSearch, setGranteeSearch] = useState("");
  const [submittedGranteeSearch, setSubmittedGranteeSearch] = useState("");
  const grantees = useQuery({
    queryKey: ["api-v2", "booking-settings", "access-grantees", submittedGranteeSearch],
    queryFn: ({ signal }) => searchBookingSettingsGrantees(submittedGranteeSearch, token, signal),
    enabled: submittedGranteeSearch.length >= 2,
  });
  const form = useForm({ schema: SchedulingSettingsSchema, initialInput: settings });
  const mutation = useMutation({
    mutationFn: (input: SchedulingSettings) =>
      saveBookingSettings(
        {
          ...input,
          ...displaySettings,
          defaultSharedWith,
          selectedGranteeKeys: selectedGrantees.map(({ key }) => key),
        } as BookingSettingsInput,
        settings.configurationVersion,
        token,
      ),
    onSuccess: async (saved) => {
      queryClient.setQueryData(["api-v2", "booking-settings", "admin"], saved);
      reset(form, { initialInput: saved });
      setDisplaySettings({
        availabilityWindowStart: saved.availabilityWindowStart,
        availabilityWindowEnd: saved.availabilityWindowEnd,
        timezoneMode: saved.timezoneMode,
        customTimezone: saved.customTimezone,
      });
      setDefaultSharedWith(saved.defaultSharedWith);
      setSelectedGrantees(saved.selectedAccessGrantees);
      await queryClient.invalidateQueries({ queryKey: bookingDisplayPreferencesQueryKey });
    },
  });
  const displaySettingsValid = v.safeParse(BookingDisplayPreferencesInputSchema, displaySettings).success;
  const sharingValid = defaultSharedWith !== "SELECTED" || selectedGrantees.length > 0;
  const dirty =
    isDirty(form) ||
    JSON.stringify(displaySettings) !==
      JSON.stringify({
        availabilityWindowStart: settings.availabilityWindowStart,
        availabilityWindowEnd: settings.availabilityWindowEnd,
        timezoneMode: settings.timezoneMode,
        customTimezone: settings.customTimezone,
      }) ||
    defaultSharedWith !== settings.defaultSharedWith ||
    selectedGrantees
      .map(({ key }) => key)
      .toSorted()
      .join("\0") !==
      settings.selectedAccessGrantees
        .map(({ key }) => key)
        .toSorted()
        .join("\0");

  return (
    <main className="p-4 sm:p-8">
      <DirtyNavigationGuard dirty={dirty} />
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
        <Separator />
        <fieldset className="space-y-4">
          <legend className="font-medium">{t("settings.defaultSharing.title")}</legend>
          <p className="text-sm text-muted-foreground">{t("settings.defaultSharing.description")}</p>
          <div className="grid gap-3">
            {(["ALL_USERS", "SELECTED", "ONLY_ME"] as const).map((mode) => (
              <label key={mode} className="flex items-start gap-2">
                <input
                  type="radio"
                  name="default-shared-with"
                  value={mode}
                  checked={defaultSharedWith === mode}
                  disabled={mutation.isPending}
                  onChange={() => setDefaultSharedWith(mode)}
                />
                <span>
                  <span className="block font-medium">{t(`settings.defaultSharing.options.${mode}.label`)}</span>
                  <span className="block text-sm text-muted-foreground">
                    {t(`settings.defaultSharing.options.${mode}.description`)}
                  </span>
                </span>
              </label>
            ))}
          </div>
          {defaultSharedWith === "SELECTED" ? (
            <div className="space-y-3 rounded-2xl border p-4">
              <div className="space-y-2">
                <label htmlFor="booking-default-access-search" className="text-sm font-medium">
                  {t("settings.defaultSharing.addUserOrGroup")}
                </label>
                <div className="flex flex-wrap gap-2">
                  <Input
                    id="booking-default-access-search"
                    value={granteeSearch}
                    minLength={2}
                    disabled={mutation.isPending}
                    onChange={(event) => setGranteeSearch(event.currentTarget.value)}
                    className="min-w-48 flex-1"
                  />
                  <Button
                    type="button"
                    variant="outline"
                    disabled={granteeSearch.trim().length < 2}
                    onClick={() => setSubmittedGranteeSearch(granteeSearch.trim())}
                  >
                    {t("settings.defaultSharing.search")}
                  </Button>
                </div>
              </div>
              {grantees.data ? (
                <ul aria-label={t("settings.defaultSharing.searchResults")} className="grid gap-2">
                  {grantees.data
                    .filter(({ key }) => !selectedGrantees.some((selected) => selected.key === key))
                    .map((grantee) => (
                      <li key={grantee.key} className="flex flex-wrap items-center justify-between gap-2">
                        <span>{grantee.name}</span>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => setSelectedGrantees((current) => [...current, grantee])}
                        >
                          {t("settings.defaultSharing.addNamed", { name: grantee.name })}
                        </Button>
                      </li>
                    ))}
                </ul>
              ) : null}
              {grantees.isFetching ? <p role="status">{t("settings.defaultSharing.loading")}</p> : null}
              {grantees.isError ? <p role="alert">{t("settings.defaultSharing.error")}</p> : null}
              {grantees.isSuccess && grantees.data.length === 0 ? (
                <p>{t("settings.defaultSharing.noResults")}</p>
              ) : null}
              <ul aria-label={t("settings.defaultSharing.selected")} className="grid gap-2">
                {selectedGrantees.map((grantee) => (
                  <li
                    key={grantee.key}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-muted p-3"
                  >
                    <span>
                      {grantee.name}
                      {"available" in grantee && !grantee.available
                        ? ` — ${t("settings.defaultSharing.unavailable")}`
                        : null}
                    </span>
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      onClick={() => setSelectedGrantees((current) => current.filter(({ key }) => key !== grantee.key))}
                    >
                      {t("settings.defaultSharing.removeNamed", { name: grantee.name })}
                    </Button>
                  </li>
                ))}
              </ul>
              {!sharingValid ? <FieldError>{t("settings.defaultSharing.required")}</FieldError> : null}
            </div>
          ) : null}
        </fieldset>
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
          disabled={mutation.isPending || !dirty || !displaySettingsValid || !sharingValid}
          aria-busy={mutation.isPending}
        >
          {t("settings.actions.save")}
        </Button>
      </Form>
    </main>
  );
}
