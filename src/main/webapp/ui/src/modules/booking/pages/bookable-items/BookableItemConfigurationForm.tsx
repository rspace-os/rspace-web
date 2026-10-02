import { Form, isDirty, reset, useForm } from "@formisch/react";
import { Fragment, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { SchedulingSettingsFields } from "@/modules/booking/configuration/schedulingSettings";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { Button } from "@/modules/common/ui/button";
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

/**
 * Each edit session keeps the version and input it opened with, even after a background refetch. After a
 * conflict the draft is kept and the next Save targets the version loaded by the conflict refresh, unless
 * the user discards the draft to load the latest configuration.
 */
export function BookableItemConfigurationForm({
  configuration,
  globalId,
  formId,
  pending,
  staleEdit,
  conflictVersion = null,
  failed,
  onSubmit,
  onDiscardConflictedDraft,
  onSaveBlockedChange,
}: {
  configuration: BookingConfiguration;
  globalId: string;
  formId: string;
  pending: boolean;
  staleEdit: boolean;
  /** The server version loaded after the last conflict, or null when the draft has not conflicted. */
  conflictVersion?: number | null;
  failed: boolean;
  onSubmit: (input: BookingConfigurationUpdateInput, version: number) => Promise<unknown>;
  /** Called after the draft is replaced by the latest configuration, so the owner can clear the conflict. */
  onDiscardConflictedDraft?: () => void;
  /** True until a field changes, and while an opening-hours day edit is unconfirmed or no day is open; the owner disables Save. */
  onSaveBlockedChange?: (blocked: boolean) => void;
}) {
  const { t } = useTranslation("booking");
  const [version, setVersion] = useState(configuration.configurationVersion);
  // Remounts the fields on discard, so an unconfirmed opening-hours edit does not outlive the draft.
  const [draftGeneration, setDraftGeneration] = useState(0);
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
  const discardDraft = () => {
    reset(form, { initialInput: configurationInput(configuration) });
    setVersion(configuration.configurationVersion);
    setDraftGeneration((generation) => generation + 1);
    onDiscardConflictedDraft?.();
    // The discard button leaves with the conflict alert, so move focus to the reloaded fields.
    requestAnimationFrame(() =>
      document
        .getElementById(formId)
        ?.querySelector<HTMLElement>("input:not([disabled]), button:not([disabled])")
        ?.focus(),
    );
  };
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
        onSubmit={(input) => (saveBlocked ? undefined : onSubmit(input, conflictVersion ?? version))}
      >
        <Fragment key={draftGeneration}>
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
        </Fragment>
        {staleEdit ? (
          <div className="space-y-2">
            <p role="alert" className="text-sm text-destructive">
              {t("bookableItems.staleEdit")}
            </p>
            <Button type="button" size="sm" variant="outline" disabled={pending} onClick={discardDraft}>
              {t("bookableItems.actions.discardDraft")}
            </Button>
          </div>
        ) : failed ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.editError")}
          </p>
        ) : null}
      </Form>
    </>
  );
}
