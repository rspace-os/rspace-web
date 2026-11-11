import { Form, isDirty, useField, useForm } from "@formisch/react";
import type { QueryClient } from "@tanstack/react-query";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import {
  type BookingSettings,
  SchedulingSettingsFields,
  SchedulingSettingsSchema,
  schedulingSettingsEntries,
  schedulingSettingsFieldNames,
} from "@/modules/booking/configuration/schedulingSettings";
import { bookingTimeZoneOptions } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useBookingNotices } from "@/modules/booking/feedback/BookingNotices";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import {
  getCountBookingCatalogueAvailabilityQueryKey,
  getSearchBookingCatalogueLocationsQueryKey,
  getSearchBookingCatalogueQueryKey,
  getSearchCalendarCatalogueQueryKey,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import { getSearchBookingConfigurationTargetsQueryKey } from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";
import {
  invalidateListBookingConfigurations,
  useCreateBookingConfiguration,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { BookingConfigurationsCreate } from "@/modules/common/api/v2/generated/models/bookingConfigurationsCreate";
import {
  BookingConfigurationsCreateSchema,
  BookingConfigurationTargetSchema,
} from "@/modules/common/api/v2/generated/valibot/schemas";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { databaseId } from "@/modules/common/relationship-picker/queries";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import type { RelationshipSource } from "@/modules/common/relationship-picker/sources";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { instrumentRelationshipOption } from "@/modules/inventory/instrumentRelationshipSource";
import { bookingConfigurationFields } from "./bookingConfiguration";
import { MINIMUM_TARGET_QUERY_LENGTH, searchBookingTargets } from "./bookingConfigurationTargets";

const createBookableItemFormSchema = v.intersect([
  v.object({ ...BookingConfigurationsCreateSchema.entries, enabled: v.boolean(), ...schedulingSettingsEntries }),
  SchedulingSettingsSchema,
]);

const TARGET_CONFLICT = "errors.api.v2.bookingConfiguration.target.conflict";
const detailFields = bookingConfigurationFields.filter((field) => field.name !== "target");

function pick<T, K extends keyof T>(source: T, keys: readonly K[]): Pick<T, K> {
  return Object.fromEntries(keys.map((key) => [key, source[key]])) as Pick<T, K>;
}

// The JVM institution zone may be an alias (e.g. Etc/UTC) missing from Intl's canonical list.
function withInstitutionTimeZone(institutionTimezone: string) {
  return detailFields.map((field) =>
    field.name === "timezone" && field.type === "select"
      ? { ...field, options: bookingTimeZoneOptions(institutionTimezone) }
      : field,
  );
}

type TargetSelection = { type: "empty" } | { type: "instrument"; id: number } | { type: "unsupported" };

const bookingConfigurationTargetSource: RelationshipSource = {
  id: "booking-configuration-targets",
  globalIdPrefix: "IN",
  search: (term, signal) =>
    term.trim().length > 0 && term.trim().length < MINIMUM_TARGET_QUERY_LENGTH
      ? Promise.resolve([])
      : searchBookingTargets(term, signal),
  batchSize: 1,
  resolveMany: async (values, signal) =>
    Object.fromEntries(
      await Promise.all(
        values.map(async (value) => {
          const targets = await searchBookingTargets(value, signal);
          return [value, targets.find((target) => target.globalId?.toUpperCase() === value.toUpperCase()) ?? null];
        }),
      ),
    ),
  ownsValue: (value) => /^IN\d+$/i.test(value.trim()),
  toOption: (document, context) => {
    const instrument = parseOrThrow(BookingConfigurationTargetSchema, document);
    if (
      instrument.globalId === undefined ||
      instrument.name === undefined ||
      instrument.id === undefined ||
      instrument.deleted !== false
    )
      throw new Error("Booking target identity is missing");
    return instrumentRelationshipOption(instrument, context);
  },
};

function targetSelection(input: unknown): TargetSelection {
  if (
    input === undefined ||
    input === null ||
    input === "" ||
    (typeof input === "object" &&
      (Object.keys(input).length === 0 ||
        ("value" in input && (input.value === undefined || input.value === null || input.value === ""))))
  ) {
    return { type: "empty" };
  }
  if (typeof input !== "object" || !("relationTo" in input) || !("value" in input)) return { type: "unsupported" };
  return input.relationTo === "instruments" && typeof input.value === "number"
    ? { type: "instrument", id: input.value }
    : { type: "unsupported" };
}

export function AddBookableItemForm({
  defaults,

  initialTargetId,
  initialTargetName,
}: {
  defaults: BookingSettings;

  initialTargetId?: number;
  initialTargetName?: string;
}) {
  const { t } = useTranslation("booking");
  const queryClient = useQueryClient();
  const notices = useBookingNotices();
  const navigate = useNavigate({ from: "/booking/bookable-items/add/" });
  const form = useForm({
    schema: createBookableItemFormSchema,
    initialInput: {
      ...(initialTargetId === undefined ? {} : { target: { relationTo: "instruments", value: initialTargetId } }),
      enabled: true,
      timezone: defaults.institutionTimezone,
      ...pick(defaults, schedulingSettingsFieldNames),
    },
  });
  const fields = useMemo(() => withInstitutionTimeZone(defaults.institutionTimezone), [defaults.institutionTimezone]);
  const targetField = useField(form, { path: ["target"] });
  const [selectedTargetName, setSelectedTargetName] = useState(initialTargetName);
  const target = targetSelection(targetField.input);
  const selectedTargetId = target.type === "instrument" ? target.id : undefined;
  const createOperation = useCreateBookingConfiguration();
  const createMutation = useMutation({
    mutationFn: (input: BookingConfigurationsCreate) => createOperation.mutateAsync({ data: input }),
    onSuccess: async (created, input) => {
      const globalId = created?.target?.globalId ?? `IN${input.target.value}`;
      notices.notify("bookable-items", {
        id: `bookable-item-${globalId}`,
        tone: "success",
        message: t("bookableItems.feedback.added", {
          item: selectedTargetName ?? globalId,
        }),
        actions: (
          <>
            <Link
              to="/booking/bookable-items/$globalId/{-$tab}"
              params={{ globalId, tab: undefined }}
              className={buttonVariants({ variant: "outline", size: "sm" })}
            >
              {t("bookableItems.feedback.viewDetails")}
            </Link>
            {input.enabled ? (
              <Link
                to="/booking/calendar"
                search={{ target: globalId }}
                className={buttonVariants({ variant: "outline", size: "sm" })}
              >
                {t("bookableItems.feedback.viewCalendar")}
              </Link>
            ) : null}
          </>
        ),
      });

      // The new configuration is readable, and its instrument is no longer an eligible target.
      await Promise.all([
        invalidateListBookingConfigurations(queryClient, undefined, { cancelRefetch: false }),
        joinBookingCatalogueRefresh(queryClient),
        queryClient.invalidateQueries({ queryKey: getSearchBookingConfigurationTargetsQueryKey() }),
      ]);
      await navigate({ to: "/booking/all-items", ignoreBlocker: true });
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.code === TARGET_CONFLICT) {
        await queryClient.invalidateQueries({ queryKey: getSearchBookingConfigurationTargetsQueryKey() });
      }
    },
  });
  const failedForSelectedTarget = createMutation.variables?.target.value === selectedTargetId;
  const conflict =
    createMutation.error instanceof ApiV2ProblemError &&
    createMutation.error.code === TARGET_CONFLICT &&
    failedForSelectedTarget
      ? createMutation.error
      : null;
  const canComplete = target.type === "instrument" && conflict === null;
  const createFailed = createMutation.isError && failedForSelectedTarget && conflict === null;

  return (
    <main className="p-4 sm:p-8">
      <DirtyNavigationGuard dirty={isDirty(form) && !createMutation.isSuccess} />
      <Heading level={3} as="h1" className="mb-5">
        {t("bookableItems.addTitle")}
      </Heading>
      <Separator className="mb-8 h-px bg-gray-300" />
      <Form of={form} className="max-w-2xl space-y-8" onSubmit={(input) => createMutation.mutateAsync(input)}>
        <div className="space-y-3">
          <label htmlFor="booking-configuration-target-search" className="font-medium">
            {t("bookableItems.targetSearch.label")}
          </label>
          <RelationshipPicker
            sources={[bookingConfigurationTargetSource]}
            value={selectedTargetId === undefined ? "" : `IN${selectedTargetId}`}
            onChange={(globalId) => {
              const id = databaseId(bookingConfigurationTargetSource, globalId);
              targetField.onChange(
                id === null ? { relationTo: undefined, value: undefined } : { relationTo: "instruments", value: id },
              );
            }}
            onOptionChange={(option) => setSelectedTargetName(option?.label)}
            disabled={createMutation.isPending}
            id="booking-configuration-target-search"
            name="target"
            required
            ariaLabel={t("bookableItems.targetSearch.label")}
            className="w-full rounded-sm"
            browseWhenEmpty
            browseEmptyMessage={t("bookableItems.targetSearch.noEligible")}
            searchDebounceMs={250}
            minimumSearchLength={MINIMUM_TARGET_QUERY_LENGTH}
          />
        </div>
        {target.type === "unsupported" ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.availability.instrumentRequired")}
          </p>
        ) : target.type === "instrument" && conflict !== null ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.availability.alreadyConfigured")}{" "}
            <Link
              to="/booking/bookable-items/$globalId/{-$tab}"
              params={{ globalId: `IN${target.id}`, tab: undefined }}
              className="underline"
            >
              {t("bookableItems.availability.viewExisting")}
            </Link>
          </p>
        ) : null}
        {canComplete ? (
          <>
            <RenderFields fields={fields} form={form} disabled={createMutation.isPending} />
            <SchedulingSettingsFields form={form} disabled={createMutation.isPending} />
            {createFailed ? (
              <p role="alert" className="text-sm text-destructive">
                {t("bookableItems.addError")}
              </p>
            ) : null}
            <Button
              type="submit"
              className="rounded-sm"
              disabled={createMutation.isPending}
              aria-busy={createMutation.isPending}
            >
              {t("bookableItems.actions.submit")}
            </Button>
          </>
        ) : null}
      </Form>
    </main>
  );
}

function joinBookingCatalogueRefresh(queryClient: QueryClient) {
  return Promise.all(
    [
      getSearchBookingCatalogueQueryKey(),
      getSearchBookingCatalogueLocationsQueryKey(),
      getSearchCalendarCatalogueQueryKey(),
      getCountBookingCatalogueAvailabilityQueryKey(),
    ].map((queryKey) => queryClient.invalidateQueries({ queryKey }, { cancelRefetch: false })),
  );
}
