import { Form, isDirty, useForm } from "@formisch/react";
import { pick } from "es-toolkit";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import {
  SchedulingSettingsFields,
  SchedulingSettingsSchema,
  schedulingSettingsEntries,
  schedulingSettingsFieldNames,
} from "@/modules/booking/configuration/schedulingSettings";
import type { BookingConfigurationsUpdate } from "@/modules/common/api/v2/generated/models/bookingConfigurationsUpdate";
import { BookingConfigurationsUpdateSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/modules/common/ui/alert-dialog";
import { type BookingConfiguration, bookingConfigurationFields } from "./bookingConfiguration";

const bookableItemConfigurationFormSchema = v.intersect([
  v.object({ ...BookingConfigurationsUpdateSchema.entries, enabled: v.boolean(), ...schedulingSettingsEntries }),
  SchedulingSettingsSchema,
]);

function configurationInput(configuration: BookingConfiguration) {
  return {
    ...pick(configuration, ["enabled", ...schedulingSettingsFieldNames]),
    openingHours: configuration.openingHours.map((entry) => ({ ...entry })),
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
  onSubmit: (input: BookingConfigurationsUpdate, version: number) => Promise<unknown>;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const [version] = useState(configuration.configurationVersion);
  const [openedEnabled] = useState(configuration.enabled);
  const [pendingDisable, setPendingDisable] = useState<{
    input: BookingConfigurationsUpdate;
    version: number;
  } | null>(null);
  const form = useForm({
    schema: bookableItemConfigurationFormSchema,
    initialInput: configurationInput(configuration),
  });
  return (
    <>
      <DirtyNavigationGuard
        dirty={isDirty(form)}
        shouldBlockNavigation={({ current, next }) =>
          current.pathname !== next.pathname &&
          (next.routeId !== "/booking/bookable-items/$globalId/{-$tab}/" ||
            next.params.globalId !== globalId ||
            !next.search.edit)
        }
      />
      <Form
        id={formId}
        of={form}
        className="min-w-0 space-y-4"
        onSubmit={(input) => {
          if (openedEnabled && !input.enabled) {
            setPendingDisable({ input, version });
            return;
          }
          return onSubmit(input, version);
        }}
      >
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
      <AlertDialog
        open={pendingDisable !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDisable(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookableItemDetails.disableDialog.title", {})}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("bookableItemDetails.disableDialog.description", {
                item: configuration.target?.value.name ?? t("common:values.unknownItem"),
              })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={pending}>{t("common:actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              disabled={pending}
              onClick={() => {
                const submission = pendingDisable;
                setPendingDisable(null);
                if (submission) void onSubmit(submission.input, submission.version).catch(() => undefined);
              }}
            >
              {t("bookableItemDetails.disableDialog.confirm", {})}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
