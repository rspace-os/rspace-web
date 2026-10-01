import { Form, isDirty, useForm } from "@formisch/react";
import { useEffect, useState } from "react";
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
    openDays: configuration.openDays,
    openingExceptions: configuration.openingExceptions,
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
  onSaveBlockedChange,
}: {
  configuration: BookingConfiguration;
  globalId: string;
  formId: string;
  pending: boolean;
  staleEdit: boolean;
  failed: boolean;
  onSubmit: (input: BookingConfigurationUpdateInput, version: number) => Promise<unknown>;
  /** True until a field changes, and while an opening-hours day edit is unconfirmed or no day is open; the owner disables Save. */
  onSaveBlockedChange?: (blocked: boolean) => void;
}) {
  const { t } = useTranslation("booking");
  const [version] = useState(configuration.configurationVersion);
  const [schedulingBlocked, setSchedulingBlocked] = useState(false);
  const form = useForm({
    schema: BookingConfigurationUpdateInputSchema,
    initialInput: configurationInput(configuration),
  });
  const dirty = isDirty(form);
  // An unchanged form has nothing to save, so Save waits for a change as well as for the scheduling fields.
  const saveBlocked = schedulingBlocked || !dirty;
  useEffect(() => {
    onSaveBlockedChange?.(saveBlocked);
  }, [onSaveBlockedChange, saveBlocked]);
  return (
    <>
      <DirtyNavigationGuard
        dirty={dirty}
        shouldBlockNavigation={({ current, next }) =>
          current.pathname !== next.pathname &&
          (next.routeId !== "/booking/bookable-items/$globalId/{-$tab}" ||
            next.params.globalId !== globalId ||
            !next.search.edit)
        }
      />
      <Form
        id={formId}
        of={form}
        className="min-w-0 space-y-4"
        onSubmit={(input) => (saveBlocked ? undefined : onSubmit(input, version))}
      >
        <RenderFields
          fields={bookingConfigurationFields.filter((field) => field.name !== "target" && field.name !== "timezone")}
          form={form}
          disabled={pending}
          layout="inline"
        />
        <SchedulingSettingsFields
          form={form}
          disabled={pending}
          layout="inline"
          onSaveBlockedChange={setSchedulingBlocked}
        />
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
