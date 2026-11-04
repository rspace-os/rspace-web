import { Form, isDirty, useField, useForm } from "@formisch/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { type BookingSettings, SchedulingSettingsFields } from "@/modules/booking/configuration/schedulingSettings";
import { ApiV2ProblemError, parseApiV2Problem } from "@/modules/booking/domain/booking";
import { bookingTimeZoneOptions } from "@/modules/booking/domain/bookingDisplayPreferences";
import { apiV2JsonHeaders } from "@/modules/common/api/apiV2Headers";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { databaseId } from "@/modules/common/relationship-picker/queries";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import { instrumentRelationshipOption, type RelationshipSource } from "@/modules/common/relationship-picker/sources";
import { Button } from "@/modules/common/ui/button";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import {
  type BookingConfigurationInput,
  BookingConfigurationInputSchema,
  bookingConfigurationFields,
  invalidateBookingCatalogueQueries,
} from "./bookingConfiguration";
import { BookingTargetSchema, MINIMUM_TARGET_QUERY_LENGTH, searchBookingTargets } from "./bookingConfigurationTargets";

const TARGET_CONFLICT = "errors.api.v2.bookingConfiguration.target.conflict";
const detailFields = bookingConfigurationFields.filter((field) => field.name !== "target");

// The JVM institution zone may be an alias (e.g. Etc/UTC) missing from Intl's canonical list.
function withInstitutionTimeZone(institutionTimezone: string) {
  return detailFields.map((field) =>
    field.name === "timezone" && field.type === "select"
      ? { ...field, options: bookingTimeZoneOptions(institutionTimezone) }
      : field,
  );
}

async function createBookingConfiguration(input: BookingConfigurationInput, token: string): Promise<void> {
  const response = await fetchWithUiToken("/api/v2/booking-configurations", {
    method: "POST",
    headers: apiV2JsonHeaders(token),
    body: JSON.stringify(input),
  });
  if (response.ok) return;
  throw await parseApiV2Problem(response);
}

type TargetSelection = { type: "empty" } | { type: "instrument"; id: number } | { type: "unsupported" };

const bookingConfigurationTargetSource: RelationshipSource = {
  id: "booking-configuration-targets",
  globalIdPrefix: "IN",
  search: (term, token, signal) =>
    term.trim().length > 0 && term.trim().length < MINIMUM_TARGET_QUERY_LENGTH
      ? Promise.resolve([])
      : searchBookingTargets(term, token, signal),
  batchSize: 1,
  resolveMany: async (values, token, signal) =>
    Object.fromEntries(
      await Promise.all(
        values.map(async (value) => {
          const targets = await searchBookingTargets(value, token, signal);
          return [value, targets.find((target) => target.globalId.toUpperCase() === value.toUpperCase()) ?? null];
        }),
      ),
    ),
  ownsValue: (value) => /^IN\d+$/i.test(value.trim()),
  toOption: (document, context) => instrumentRelationshipOption(parseOrThrow(BookingTargetSchema, document), context),
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
  token,
  initialTargetId,
}: {
  defaults: BookingSettings;
  token: string;
  initialTargetId?: number;
}) {
  const { t } = useTranslation("booking");
  const queryClient = useQueryClient();
  const navigate = useNavigate({ from: "/booking/bookable-items/add/" });
  const form = useForm({
    schema: BookingConfigurationInputSchema,
    initialInput: {
      ...(initialTargetId === undefined ? {} : { target: { relationTo: "instruments", value: initialTargetId } }),
      enabled: true,
      timezone: defaults.institutionTimezone,
      slotGranularityMinutes: defaults.slotGranularityMinutes,
      openingHours: defaults.openingHours,
      bufferBeforeMinutes: defaults.bufferBeforeMinutes,
      bufferAfterMinutes: defaults.bufferAfterMinutes,
      maxBookingDurationMinutes: defaults.maxBookingDurationMinutes,
      allowDoubleBooking: defaults.allowDoubleBooking,
    },
  });
  const fields = useMemo(() => withInstitutionTimeZone(defaults.institutionTimezone), [defaults.institutionTimezone]);
  const targetField = useField(form, { path: ["target"] });
  const target = targetSelection(targetField.input);
  const selectedTargetId = target.type === "instrument" ? target.id : undefined;
  const createMutation = useMutation({
    mutationFn: (input: BookingConfigurationInput) => createBookingConfiguration(input, token),
    onSuccess: async () => {
      // The new configuration is readable, and its instrument is no longer an eligible target.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        invalidateBookingCatalogueQueries(queryClient),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configuration-targets"] }),
      ]);
      await navigate({ to: "/booking/config/bookable-items", ignoreBlocker: true });
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.code === TARGET_CONFLICT) {
        await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations", "availability"] });
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
            disabled={createMutation.isPending}
            id="booking-configuration-target-search"
            name="target"
            required
            ariaLabel={t("bookableItems.targetSearch.label")}
            className="w-full rounded-sm"
            browseWhenEmpty
            browseEmptyMessage={t("bookableItems.targetSearch.noEligible")}
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
