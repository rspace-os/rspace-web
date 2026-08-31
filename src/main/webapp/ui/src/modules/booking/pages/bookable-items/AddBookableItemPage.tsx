import { Form, useField, useForm } from "@formisch/react";
import { useMutation, useQuery, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { loadBookingSettings, SchedulingSettingsFields } from "@/modules/booking/configuration/schedulingSettings";
import { bookingApiV2JsonHeaders } from "@/modules/booking/domain/apiV2";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { Button } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import {
  type BookingConfigurationInput,
  BookingConfigurationInputSchema,
  bookingConfigurationFields,
} from "./bookingConfiguration";

const TARGET_CONFLICT = "errors.api.v2.bookingConfiguration.target.conflict";
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
  ) {
    super(`Booking configuration create failed with status ${status}`);
  }
}

async function createBookingConfiguration(input: BookingConfigurationInput, token: string): Promise<void> {
  const response = await fetch("/api/v2/booking-configurations", {
    method: "POST",
    headers: bookingApiV2JsonHeaders(token),
    body: JSON.stringify(input),
  });
  if (response.ok) return;
  const body = await response.json().catch(() => null);
  const problem = v.safeParse(ApiV2ProblemSchema, body);
  const code = problem.success ? problem.output.code : undefined;
  const detail = problem.success ? (problem.output.detail ?? undefined) : undefined;
  throw new BookingConfigurationCreateError(response.status, code, detail, input.target.value);
}

type TargetSelection = { type: "empty" } | { type: "instrument"; id: number } | { type: "unsupported" };

const BookingTargetSchema = v.object({
  id: v.number(),
  globalId: v.string(),
  name: v.string(),
  deleted: v.literal(false),
});

async function searchBookingTargets(query: string, token: string, signal?: AbortSignal) {
  const parameters = new URLSearchParams({ query, limit: "20" });
  const response = await fetch(`/api/v2/booking-configuration-targets?${parameters}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Booking target search failed with status ${response.status}`);
  return parseOrThrow(v.array(BookingTargetSchema), (await response.json()) as unknown);
}

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

export default function AddBookableItemPage() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const defaults = useSuspenseQuery({
    queryKey: ["api-v2", "booking-settings"],
    queryFn: ({ signal }) => loadBookingSettings(token, signal),
  }).data;
  const queryClient = useQueryClient();
  const navigate = useNavigate({ from: "/booking/bookable-items/add" });
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
  const targetField = useField(form, { path: ["target"] });
  const target = targetSelection(targetField.input);
  const [targetSearch, setTargetSearch] = useState("");
  const [submittedTargetSearch, setSubmittedTargetSearch] = useState("");
  const targetResults = useQuery({
    queryKey: ["api-v2", "booking-configuration-targets", submittedTargetSearch],
    queryFn: ({ signal }) => searchBookingTargets(submittedTargetSearch, token, signal),
    enabled: submittedTargetSearch.length >= 2,
  });
  const selectedTargetId = target.type === "instrument" ? target.id : undefined;
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
  const alreadyConfigured = conflict !== null;
  const canComplete = target.type === "instrument" && !alreadyConfigured;
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
        <div className="space-y-3">
          <label htmlFor="booking-configuration-target-search" className="font-medium">
            {t("bookableItems.targetSearch.label")}
          </label>
          <div className="flex flex-wrap gap-2">
            <Input
              id="booking-configuration-target-search"
              value={targetSearch}
              minLength={2}
              disabled={createMutation.isPending}
              onChange={(event) => setTargetSearch(event.currentTarget.value)}
              className="min-w-48 flex-1"
            />
            <Button
              type="button"
              variant="outline"
              disabled={targetSearch.trim().length < 2 || targetResults.isFetching}
              onClick={() => setSubmittedTargetSearch(targetSearch.trim())}
            >
              {t("bookableItems.targetSearch.search")}
            </Button>
          </div>
          {targetResults.isError ? <FieldError>{t("bookableItems.targetSearch.error")}</FieldError> : null}
          {targetResults.data ? (
            <ul aria-label={t("bookableItems.targetSearch.results")} className="grid gap-2">
              {targetResults.data.map((option) => (
                <li key={option.id}>
                  <Button
                    type="button"
                    variant={selectedTargetId === option.id ? "default" : "outline"}
                    className="w-full justify-start"
                    onClick={() => targetField.onChange({ relationTo: "booking-instruments", value: option.id })}
                  >
                    {`${option.name} (${option.globalId})`}
                  </Button>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
        {target.type === "unsupported" ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.availability.instrumentRequired")}
          </p>
        ) : target.type === "instrument" && alreadyConfigured ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.availability.alreadyConfigured")}{" "}
            <Link to="/booking/bookable-items/$globalId" params={{ globalId: `IN${target.id}` }} className="underline">
              {t("bookableItems.availability.viewExisting")}
            </Link>
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
