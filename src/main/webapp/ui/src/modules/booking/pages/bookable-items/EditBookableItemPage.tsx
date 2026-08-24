import { Form, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { bookingApiV2JsonHeaders } from "@/modules/booking/domain/apiV2";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button } from "@/modules/common/ui/button";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import {
  BOOKING_CONFIGURATION_READ_FIELDS,
  type BookingConfigurationUpdateInput,
  BookingConfigurationUpdateInputSchema,
  bookingConfigurationFields,
  fetchBookingConfiguration,
} from "./bookingConfiguration";
import { SchedulingSettingsFields } from "./schedulingSettings";

function requestUrl(id: number): string {
  const search = new URLSearchParams({
    depth: "1",
    "fields[booking-configurations]": BOOKING_CONFIGURATION_READ_FIELDS,
  });
  return `/api/v2/booking-configurations/${id}?${search}`;
}

async function updateBookingConfiguration(
  id: number,
  input: BookingConfigurationUpdateInput,
  token: string,
): Promise<void> {
  const response = await fetch(requestUrl(id), {
    method: "PATCH",
    headers: bookingApiV2JsonHeaders(token),
    body: JSON.stringify(input),
  });
  if (!response.ok) throw new Error(`Booking configuration update failed with status ${response.status}`);
}

export default function EditBookableItemPage() {
  const { t } = useTranslation("booking");
  const { id } = useParams({ from: "/booking/config/bookable-items/$id/edit" });
  const configurationId = Number(id);
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const navigate = useNavigate({ from: "/booking/config/bookable-items/$id/edit" });
  const configuration = useSuspenseQuery({
    queryKey: ["api-v2", "booking-configurations", id],
    queryFn: ({ signal }) => fetchBookingConfiguration(configurationId, token, signal),
  }).data;
  const form = useForm({
    schema: BookingConfigurationUpdateInputSchema,
    initialInput: {
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
    mutationFn: (input: BookingConfigurationUpdateInput) => updateBookingConfiguration(configurationId, input, token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      if (configuration.target === null) {
        await navigate({ to: "/booking/config/bookable-items" });
      } else {
        await navigate({
          to: "/booking/bookable-items/$globalId",
          params: { globalId: configuration.target.globalId },
        });
      }
    },
  });

  return (
    <main className="p-4 sm:p-8">
      <Heading level={2} as="h1" className="mb-5">
        {t("bookableItems.editTitle")}
      </Heading>
      <Separator className="mb-8 h-px bg-gray-300" />
      <Form of={form} className="max-w-2xl space-y-8" onSubmit={(input) => updateMutation.mutateAsync(input)}>
        <div>
          <p className="mb-2 text-sm font-medium">{t("bookableItems.fields.target")}</p>
          {configuration.target === null ? (
            <UnknownItem />
          ) : (
            <InventoryItem
              name={configuration.target.value.name}
              globalId={configuration.target.globalId}
              href={`/globalId/${configuration.target.globalId}`}
              idLinkLabel={t("bookableItems.actions.viewInventory", { globalId: configuration.target.globalId })}
            />
          )}
        </div>
        <RenderFields
          fields={bookingConfigurationFields.filter((field) => field.name !== "target")}
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
