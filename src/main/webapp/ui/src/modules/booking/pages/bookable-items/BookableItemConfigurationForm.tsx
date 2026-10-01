import { Form, isDirty, useForm } from "@formisch/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { SchedulingSettingsFields } from "@/modules/booking/configuration/schedulingSettings";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import {
  type BookingConfiguration,
  type BookingConfigurationUpdateInput,
  BookingConfigurationUpdateInputSchema,
  bookingConfigurationFields,
} from "./bookingConfiguration";

function configurationInput(configuration: BookingConfiguration): BookingConfigurationUpdateInput {
  return {
    enabled: configuration.enabled,
    slotGranularityMinutes: configuration.slotGranularityMinutes,
    openingStart: configuration.openingStart,
    openingEnd: configuration.openingEnd,
    bufferBeforeMinutes: configuration.bufferBeforeMinutes,
    bufferAfterMinutes: configuration.bufferAfterMinutes,
    maxBookingDurationMinutes: configuration.maxBookingDurationMinutes,
    allowDoubleBooking: configuration.allowDoubleBooking,
  };
}

/** Each edit session keeps the version and input it opened with, even after a refetch. */
export function BookableItemConfigurationForm({
  configuration,
  globalId,
  formId,
  pending,
  staleEdit,
  failed,
  onSubmit,
}: {
  configuration: BookingConfiguration;
  globalId: string;
  formId: string;
  pending: boolean;
  staleEdit: boolean;
  failed: boolean;
  onSubmit: (input: BookingConfigurationUpdateInput, version: number) => Promise<unknown>;
}) {
  const { t } = useTranslation("booking");
  const [version] = useState(configuration.configurationVersion);
  const form = useForm({
    schema: BookingConfigurationUpdateInputSchema,
    initialInput: configurationInput(configuration),
  });
  return (
    <>
      <DirtyNavigationGuard
        dirty={isDirty(form)}
        shouldBlockNavigation={({ current, next }) =>
          current.pathname !== next.pathname &&
          (next.routeId !== "/booking/bookable-items/$globalId/{-$tab}" ||
            next.params.globalId !== globalId ||
            !next.search.edit)
        }
      />
      <Form id={formId} of={form} className="min-w-0 space-y-4" onSubmit={(input) => onSubmit(input, version)}>
        <RenderFields
          fields={bookingConfigurationFields.filter((field) => field.name !== "target")}
          form={form}
          disabled={pending}
          layout="inline"
        />
        <SchedulingSettingsFields form={form} disabled={pending} layout="inline" />
        {staleEdit ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.staleEdit")}
          </p>
        ) : failed ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.editError")}
          </p>
        ) : null}
      </Form>
    </>
  );
}
