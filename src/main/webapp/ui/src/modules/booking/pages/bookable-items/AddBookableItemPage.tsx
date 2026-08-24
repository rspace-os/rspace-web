import { Form, useField, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button } from "@/modules/common/ui/button";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { loadUnavailableBookableItems, useSelectedBookableItemAvailability } from "./bookableItemAvailability";
import {
  type BookingConfigurationInput,
  BookingConfigurationInputSchema,
  bookingConfigurationFields,
} from "./bookingConfiguration";
import { loadBookingSettings, SchedulingSettingsFields } from "./schedulingSettings";

const TARGET_CONFLICT = "errors.api.v2.bookingConfiguration.target.conflict";
const targetFields = bookingConfigurationFields.filter((field) => field.name === "target");
const detailFields = bookingConfigurationFields.filter((field) => field.name !== "target");
const ApiV2ProblemSchema = v.looseObject({
  status: v.number(),
  code: v.string(),
  detail: v.optional(v.nullable(v.string())),
});

class BookingConfigurationCreateError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    readonly detail: string | undefined,
    readonly targetId: number,
    readonly existingConfigurationId: string | number | undefined,
  ) {
    super(`Booking configuration create failed with status ${status}`);
  }
}

async function existingConfigurationId(targetId: number, token: string): Promise<string | number | undefined> {
  const globalId = `IN${targetId}`;
  try {
    const unavailable = await loadUnavailableBookableItems([globalId], token, new AbortController().signal);
    return unavailable[globalId]?.relatedRecordId;
  } catch {
    return undefined;
  }
}

async function createBookingConfiguration(input: BookingConfigurationInput, token: string): Promise<void> {
  const response = await fetch("/api/v2/booking-configurations", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
    },
    body: JSON.stringify(input),
  });
  if (response.ok) return;
  const body = await response.json().catch(() => null);
  const problem = v.safeParse(ApiV2ProblemSchema, body);
  const code = problem.success ? problem.output.code : undefined;
  const detail = problem.success ? (problem.output.detail ?? undefined) : undefined;
  const configurationId =
    response.status === 409 && code === TARGET_CONFLICT
      ? await existingConfigurationId(input.target.value, token)
      : undefined;
  throw new BookingConfigurationCreateError(response.status, code, detail, input.target.value, configurationId);
}

type TargetSelection = { type: "empty" } | { type: "instrument"; id: number } | { type: "unsupported" };

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
  return input.relationTo === "instruments" && typeof input.value === "number"
    ? { type: "instrument", id: input.value }
    : { type: "unsupported" };
}

export default function AddBookableItemPage() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const defaults = useSuspenseQuery({
    queryKey: ["api-v2", "booking-settings"],
    queryFn: ({ signal }) => loadBookingSettings(token, signal),
  }).data;
  const queryClient = useQueryClient();
  const navigate = useNavigate({ from: "/booking/config/bookable-items/add" });
  const form = useForm({
    schema: BookingConfigurationInputSchema,
    initialInput: {
      enabled: true,
      slotGranularityMinutes: defaults.slotGranularityMinutes,
      openingStart: defaults.openingStart,
      openingEnd: defaults.openingEnd,
      bufferBeforeMinutes: defaults.bufferBeforeMinutes,
      bufferAfterMinutes: defaults.bufferAfterMinutes,
      maxBookingDurationMinutes: defaults.maxBookingDurationMinutes,
      allowDoubleBooking: defaults.allowDoubleBooking,
    },
  });
  const target = targetSelection(useField(form, { path: ["target"] }).input);
  const selectedTargetId = target.type === "instrument" ? target.id : undefined;
  const selectedTargetAvailability = useSelectedBookableItemAvailability(
    selectedTargetId === undefined ? undefined : `IN${selectedTargetId}`,
    token,
  );
  const createMutation = useMutation({
    mutationFn: (input: BookingConfigurationInput) => createBookingConfiguration(input, token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      await navigate({ to: "/booking/config/bookable-items" });
    },
    onError: async (error) => {
      if (error instanceof BookingConfigurationCreateError && error.code === TARGET_CONFLICT) {
        await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations", "availability"] });
      }
    },
  });
  const conflict =
    createMutation.error instanceof BookingConfigurationCreateError &&
    createMutation.error.code === TARGET_CONFLICT &&
    createMutation.error.targetId === selectedTargetId
      ? createMutation.error
      : null;
  const existingConfigurationId = selectedTargetAvailability.data?.relatedRecordId ?? conflict?.existingConfigurationId;
  const alreadyConfigured = selectedTargetAvailability.data != null || conflict !== null;
  const canComplete = target.type === "instrument" && selectedTargetAvailability.isSuccess && !alreadyConfigured;
  const createFailed =
    createMutation.error instanceof BookingConfigurationCreateError &&
    createMutation.error.targetId === selectedTargetId &&
    conflict === null;

  return (
    <main className="p-4 sm:p-8">
      <Heading level={2} as="h1" className="mb-5">
        {t("bookableItems.addTitle")}
      </Heading>
      <Separator className="mb-8 h-px bg-gray-300" />
      <Form of={form} className="max-w-2xl space-y-8" onSubmit={(input) => createMutation.mutateAsync(input)}>
        <RenderFields fields={targetFields} form={form} disabled={createMutation.isPending} />
        {target.type === "unsupported" ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.availability.instrumentRequired")}
          </p>
        ) : target.type === "instrument" && alreadyConfigured ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.availability.alreadyConfigured")}{" "}
            {existingConfigurationId === undefined ? null : (
              <Link
                to="/booking/config/bookable-items/$id"
                params={{ id: String(existingConfigurationId) }}
                className="underline"
              >
                {t("bookableItems.availability.editExisting")}
              </Link>
            )}
          </p>
        ) : target.type === "instrument" && selectedTargetAvailability.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.availability.checkFailed")}
          </p>
        ) : target.type === "instrument" && selectedTargetAvailability.isPending ? (
          <p role="status" className="text-sm text-muted-foreground">
            {t("bookableItems.availability.checking")}
          </p>
        ) : null}
        {canComplete ? (
          <>
            <RenderFields fields={detailFields} form={form} disabled={createMutation.isPending} />
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
