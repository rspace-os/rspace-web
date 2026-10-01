import { Form, isDirty, useField, useForm } from "@formisch/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { type BookingSettings, SchedulingSettingsFields } from "@/modules/booking/configuration/schedulingSettings";
import { bookingApiV2JsonHeaders } from "@/modules/booking/domain/apiV2";
import { ApiV2ProblemError, parseApiV2Problem } from "@/modules/booking/domain/booking";
import { bookingTimeZoneOptions } from "@/modules/booking/domain/bookingDisplayPreferences";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import { databaseId } from "@/modules/common/relationship-picker/relationshipOptionQueries";
import type { RelationshipSource } from "@/modules/common/relationship-picker/relationshipSources";
import { Button } from "@/modules/common/ui/button";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import {
  type BookingConfigurationInput,
  BookingConfigurationInputSchema,
  bookingConfigurationFields,
} from "./bookingConfiguration";

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
  const response = await fetch("/api/v2/booking-configurations", {
    method: "POST",
    headers: bookingApiV2JsonHeaders(token),
    body: JSON.stringify(input),
  });
  if (response.ok) return;
  throw await parseApiV2Problem(response);
}

type TargetSelection = { type: "empty" } | { type: "instrument"; id: number } | { type: "unsupported" };

const BookingTargetSchema = v.object({
  id: v.number(),
  globalId: v.string(),
  name: v.string(),
  deleted: v.literal(false),
});

export async function searchBookingTargets(query: string, token: string | undefined, signal?: AbortSignal) {
  const parameters = new URLSearchParams({ query, limit: "20" });
  const response = await fetch(`/api/v2/booking-configuration-targets?${parameters}`, {
    headers: { "X-Requested-With": "XMLHttpRequest", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    signal,
  });
  if (!response.ok) throw new Error(`Booking target search failed with status ${response.status}`);
  return parseOrThrow(v.array(BookingTargetSchema), (await response.json()) as unknown);
}

const bookingConfigurationTargetSource: RelationshipSource = {
  id: "booking-configuration-targets",
  globalIdPrefix: "IN",
  search: (term, token, signal) =>
    term.trim().length < 2 ? Promise.resolve([]) : searchBookingTargets(term.trim(), token, signal),
  resolve: async (value, token, signal) => {
    const targets = await searchBookingTargets(value, token, signal);
    return targets.find((target) => target.globalId.toUpperCase() === value.toUpperCase()) ?? null;
  },
  ownsValue: (value) => /^IN\d+$/i.test(value.trim()),
  toOption: (document, context) => {
    const instrument = parseOrThrow(BookingTargetSchema, document);
    return {
      value: instrument.globalId,
      label: instrument.name,
      content: (
        <InventoryItem
          name={instrument.name}
          globalId={instrument.globalId}
          href={`/globalId/${instrument.globalId}`}
          idLinkLabel={context.idLinkLabel(instrument.globalId)}
          compact={context.compact}
          size="xs"
        />
      ),
    };
  },
};

function targetSelection(input: unknown): TargetSelection {
  if (
    input === undefined ||
    input === null ||
    input === "" ||
    (typeof input === "object" && Object.keys(input).length === 0)
  ) {
    return { type: "empty" };
  }
  if (
    typeof input === "object" &&
    "value" in input &&
    (input.value === undefined || input.value === null || input.value === "")
  ) {
    return { type: "empty" };
  }
  if (typeof input !== "object" || !("relationTo" in input) || !("value" in input)) return { type: "unsupported" };
  return input.relationTo === "booking-instruments" && typeof input.value === "number"
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
  const navigate = useNavigate({ from: "/booking/bookable-items/add" });
  const form = useForm({
    schema: BookingConfigurationInputSchema,
    initialInput: {
      ...(initialTargetId === undefined
        ? {}
        : { target: { relationTo: "booking-instruments", value: initialTargetId } }),
      enabled: true,
      timezone: defaults.institutionTimezone,
      slotGranularityMinutes: defaults.slotGranularityMinutes,
      openingStart: defaults.openingStart,
      openingEnd: defaults.openingEnd,
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
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
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
  const alreadyConfigured = conflict !== null;
  const canComplete = target.type === "instrument" && !alreadyConfigured;
  const createFailed =
    createMutation.error instanceof ApiV2ProblemError && failedForSelectedTarget && conflict === null;

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
            source={bookingConfigurationTargetSource}
            value={selectedTargetId === undefined ? "" : `IN${selectedTargetId}`}
            onChange={(globalId) => {
              const id = databaseId(bookingConfigurationTargetSource, globalId);
              targetField.onChange(
                id === null
                  ? { relationTo: undefined, value: undefined }
                  : { relationTo: "booking-instruments", value: id },
              );
            }}
            disabled={createMutation.isPending}
            id="booking-configuration-target-search"
            name="target"
            required
            ariaLabel={t("bookableItems.targetSearch.label")}
            className="w-full rounded-sm"
          />
        </div>
        {target.type === "unsupported" ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.availability.instrumentRequired")}
          </p>
        ) : target.type === "instrument" && alreadyConfigured ? (
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
