import { Form, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { Button } from "@/modules/common/ui/button";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import {
  type BookingConfiguration,
  BookingConfigurationSchema,
  type BookingConfigurationUpdateInput,
  BookingConfigurationUpdateInputSchema,
  bookingConfigurationFields,
} from "./bookingConfiguration";
import { SchedulingSettingsFields } from "./schedulingSettings";

const selectedFields =
  "id,target,enabled,timezone,slotGranularityMinutes,openingStart,openingEnd,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking,updatedAt";

function requestUrl(id: string): string {
  const search = new URLSearchParams({
    depth: "1",
    "fields[booking-configurations]": selectedFields,
  });
  return `/api/v2/booking-configurations/${id}?${search}`;
}

async function getBookingConfiguration(id: string, token: string): Promise<BookingConfiguration> {
  const response = await fetch(requestUrl(id), {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Requested-With": "XMLHttpRequest",
    },
  });
  if (!response.ok) throw new Error(`Booking configuration request failed with status ${response.status}`);
  return parseOrThrow(BookingConfigurationSchema, (await response.json()) as unknown);
}

async function updateBookingConfiguration(
  id: string,
  input: BookingConfigurationUpdateInput,
  token: string,
): Promise<void> {
  const response = await fetch(requestUrl(id), {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
    },
    body: JSON.stringify(input),
  });
  if (!response.ok) throw new Error(`Booking configuration update failed with status ${response.status}`);
}

export default function BookableItemPage() {
  const { t } = useTranslation("booking");
  const { id } = useParams({ from: "/booking/config/bookable-items/$id" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const navigate = useNavigate({ from: "/booking/config/bookable-items/$id" });
  const configuration = useSuspenseQuery({
    queryKey: ["api-v2", "booking-configurations", id],
    queryFn: () => getBookingConfiguration(id, token),
  }).data;
  const form = useForm({
    schema: BookingConfigurationUpdateInputSchema,
    initialInput: {
      ...(configuration.target === null
        ? {}
        : { target: { relationTo: "instruments" as const, value: configuration.target.value.id } }),
      enabled: configuration.enabled,
      timezone: configuration.timezone,
      slotGranularityMinutes: configuration.slotGranularityMinutes,
      openingStart: configuration.openingStart,
      openingEnd: configuration.openingEnd,
      bufferBeforeMinutes: configuration.bufferBeforeMinutes,
      bufferAfterMinutes: configuration.bufferAfterMinutes,
      maxBookingDurationMinutes: configuration.maxBookingDurationMinutes,
      allowDoubleBooking: configuration.allowDoubleBooking,
    },
  });
  const updateMutation = useMutation({
    mutationFn: (input: BookingConfigurationUpdateInput) => updateBookingConfiguration(id, input, token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      await navigate({ to: "/booking/config/bookable-items" });
    },
  });

  return (
    <main className="p-4 sm:p-8">
      <Heading level={2} as="h1" className="mb-5">
        {t("bookableItems.editTitle")}
      </Heading>
      <Separator className="mb-8 h-px bg-gray-300" />
      <Form of={form} className="max-w-2xl space-y-8" onSubmit={(input) => updateMutation.mutateAsync(input)}>
        {configuration.target === null ? <UnknownItem /> : null}
        <RenderFields
          fields={bookingConfigurationFields.filter(
            (field) => configuration.target !== null || field.name !== "target",
          )}
          form={form}
          disabled={updateMutation.isPending}
        />
        <SchedulingSettingsFields form={form} disabled={updateMutation.isPending} />
        {updateMutation.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.editError")}
          </p>
        ) : null}
        <Button
          type="submit"
          className="rounded-sm"
          disabled={updateMutation.isPending}
          aria-busy={updateMutation.isPending}
        >
          {t("bookableItems.actions.save")}
        </Button>
      </Form>
    </main>
  );
}
