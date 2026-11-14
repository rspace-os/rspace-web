import { Form, isDirty } from "@formisch/react";
import { useTranslation } from "react-i18next";
import { SchedulingSettingsFields } from "@/modules/booking/configuration/schedulingSettings";
import { type BookingConfigurationsUpdate } from "@/modules/common/api/v2/generated/models/bookingConfigurationsUpdate";
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
import { type useLoadedBookableItemPageActions } from "./useLoadedBookableItemPageActions";

type ConfigurationSubmission = { input: BookingConfigurationsUpdate; version: number };

export function BookableItemConfigurationForm({
  configuration,
  globalId,
  formId,
  form,
  version,
  openedEnabled,
  pending,
  staleEdit,
  failed,
  pendingDisable,
  onPendingDisableChange,
  onSubmit,
}: {
  configuration: BookingConfiguration;
  globalId: string;
  formId: string;
  form: ReturnType<typeof useLoadedBookableItemPageActions>["form"];
  version: number;
  openedEnabled: boolean;
  pending: boolean;
  staleEdit: boolean;
  failed: boolean;
  pendingDisable: ConfigurationSubmission | null;
  onPendingDisableChange: (submission: ConfigurationSubmission | null) => void;
  onSubmit: (input: BookingConfigurationsUpdate, version: number) => Promise<unknown>;
}) {
  const { t } = useTranslation(["booking", "common"]);
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
            onPendingDisableChange({ input, version });
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
          if (!open) onPendingDisableChange(null);
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
                onPendingDisableChange(null);
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
