import { Tabs } from "@base-ui/react/tabs";
import { PencilIcon, SaveIcon, XIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { BookingCreationButtonGroup } from "@/modules/booking/creation/BookingCreationButtonGroup";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { BookingLocalNotices, useBookingNoticeHost } from "@/modules/booking/feedback/BookingNotices";
import { detailColumnsClassName, detailPageClassName } from "@/modules/common/app/detailPageLayout";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";
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
import { Button } from "@/modules/common/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Input } from "@/modules/common/ui/input";
import { Heading } from "@/modules/common/ui/typography";
import { BookableItemAuditLog } from "./BookableItemAuditLog";
import { BookableItemConfigurationForm } from "./BookableItemConfigurationForm";
import { BookableItemFactsAside } from "./BookableItemFactsAside";
import { BookableItemNotificationSubscription } from "./BookableItemNotificationSubscription";
import { BookableItemRulesReadOut } from "./BookableItemRulesReadOut";
import { BookableItemSpotlightHeader } from "./BookableItemSpotlightHeader";
import { BookingConfigurationActionsMenu } from "./BookingConfigurationActionsMenu";
import { BookingEventList } from "./BookingEventList";
import { lifecycleErrorKey } from "./bookableItemLifecycleHelpers";
import type { BookingConfiguration } from "./bookingConfiguration";
import { CalendarSubscriptionPopover } from "./CalendarSubscriptionPopover";
import { InventoryAccessReadOnly } from "./InventoryAccessReadOnly";
import { type BookableItemTab, useLoadedBookableItemPageActions } from "./useLoadedBookableItemPageActions";

export type { BookableItemTab } from "./useLoadedBookableItemPageActions";

export function bookableItemTab(tab: string | undefined): BookableItemTab {
  return tab === "details" || tab === "audit" || tab === "access" ? tab : "bookings";
}

function PageTab({ value, disabled, children }: { value: BookableItemTab; disabled: boolean; children: ReactNode }) {
  return (
    <Tabs.Tab
      value={value}
      disabled={disabled}
      className="-mb-px cursor-default border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted-foreground transition-colors outline-none select-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/30 aria-selected:border-primary aria-selected:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
    >
      {children}
    </Tabs.Tab>
  );
}

export function LoadedBookableItemPage({
  configuration,
  globalId,
  tab,
  token,
  readError,
  retrying,
  onRetry,
}: {
  configuration: BookingConfiguration;
  globalId: string;
  tab: BookableItemTab;
  token: string;
  readError: boolean;
  retrying: boolean;
  onRetry: () => void;
}) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: currentUser } = useCurrentUserQuery();
  const preferences = useBookingDisplayPreferences();
  const {
    saveAnnouncement,
    staleEdit,
    archiveOpen,
    setArchiveOpen,
    permanentDeleteOpen,
    setPermanentDeleteOpen,
    permanentDeleteConfirmation,
    setPermanentDeleteConfirmation,
    leaveOpen,
    setLeaveOpen,
    editButtonRef,
    saveButtonRef,
    actionsButtonRef,
    canEdit,
    active,
    editing,
    setEdit,
    setTab,
    updateMutation,
    archiveMutation,
    restoreMutation,
    permanentDeleteMutation,
    leaveMutation,
    cancelEdit,
    handleLifecycleAction,
    rulesSaveBlocked,
    setRulesSaveBlocked,
    conflictVersion,
    discardConflictedDraft,
    localNotices,
  } = useLoadedBookableItemPageActions({ configuration, globalId, tab, token });
  useBookingNoticeHost("item-detail", localNotices.notify);
  const [cutoff] = useState(() => new Date().toISOString());
  const formId = `bookable-item-details-${useId()}`;
  const permanentConfirmationId = `${formId}-permanent-delete-confirmation`;
  const target = configuration.target;
  const directSysadmin = currentUser.hasSysAdminRole && !currentUser.session.operatedAs;

  if (target === null) return null;

  return (
    <main className={detailPageClassName} tabIndex={-1}>
      <BookingLocalNotices alerts={localNotices.alerts} onDismiss={localNotices.dismiss} />
      {readError ? (
        <Alert variant="destructive">
          <AlertDescription>
            {t("bookableItemDetails.backgroundError")}
            <div className="mt-4">
              <Button type="button" variant="outline" disabled={retrying} aria-busy={retrying} onClick={onRetry}>
                {commonT("actions.retry")}
              </Button>
            </div>
          </AlertDescription>
        </Alert>
      ) : null}
      <div className="@container">
        <Tabs.Root
          value={tab}
          onValueChange={(value) => {
            if (updateMutation.isPending) return;
            setTab(bookableItemTab(value));
          }}
          className="min-w-0 space-y-6"
        >
          {restoreMutation.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t(
                lifecycleErrorKey(
                  restoreMutation.error,
                  "bookableItemDetails.lifecycleErrors.restore",
                  "bookableItemDetails.lifecycleErrors.stale",
                  "bookableItemDetails.lifecycleErrors.stateChanged",
                ),
              )}
            </p>
          ) : null}
          <BookableItemSpotlightHeader
            configuration={configuration}
            target={target}
            action={
              <>
                {active &&
                (configuration.capabilities.canCreateBooking || configuration.capabilities.canCreateBlockout) ? (
                  <BookingCreationButtonGroup
                    ownerId={`bookable-item-${configuration.id}`}
                    originHost="item-detail"
                    target={bookableItemOption({ ...configuration, target })}
                    lockTarget
                    disabled={!configuration.enabled}
                  />
                ) : null}
                {configuration.capabilities.canSubscribeCalendar ? (
                  <CalendarSubscriptionPopover configurationId={configuration.id} token={token} archived={!active} />
                ) : null}
                {active && configuration.capabilities.canLeaveConfiguration ? (
                  <Button type="button" variant="outline" onClick={() => setLeaveOpen(true)}>
                    {t("bookableItemDetails.actions.leave")}
                  </Button>
                ) : null}
                <BookingConfigurationActionsMenu
                  configuration={configuration}
                  itemName={target.value.name}
                  directSysadmin={directSysadmin}
                  disabled={archiveMutation.isPending || restoreMutation.isPending || permanentDeleteMutation.isPending}
                  triggerRef={actionsButtonRef}
                  onAction={handleLifecycleAction}
                />
              </>
            }
          />

          <Tabs.List className="flex flex-wrap border-b">
            <PageTab value="bookings" disabled={updateMutation.isPending}>
              {t("bookableItemDetails.tabs.bookings")}
            </PageTab>
            <PageTab value="details" disabled={updateMutation.isPending}>
              {t("bookableItemDetails.tabs.details")}
            </PageTab>
            {configuration.capabilities.canViewAudit ? (
              <PageTab value="audit" disabled={updateMutation.isPending}>
                {t("bookableItemDetails.tabs.audit")}
              </PageTab>
            ) : null}
            <PageTab value="access" disabled={updateMutation.isPending}>
              {t("bookableItemDetails.tabs.access")}
            </PageTab>
          </Tabs.List>

          <div className={detailColumnsClassName}>
            <div className="min-w-0">
              <Tabs.Panel value="bookings" className="space-y-8 outline-none">
                <section className="space-y-4" aria-labelledby="upcoming-events-heading">
                  <Heading level={3} as="h2" id="upcoming-events-heading">
                    {t("bookableItemDetails.upcoming")}
                  </Heading>
                  <BookingEventList
                    globalId={globalId}
                    timezone={preferences.timeZone}
                    period="upcoming"
                    cutoff={cutoff}
                  />
                </section>

                <section className="space-y-4" aria-labelledby="past-events-heading">
                  <Heading level={3} as="h2" id="past-events-heading">
                    {t("bookableItemDetails.past")}
                  </Heading>
                  <BookingEventList globalId={globalId} timezone={preferences.timeZone} period="past" cutoff={cutoff} />
                </section>
              </Tabs.Panel>

              <Tabs.Panel value="details" keepMounted className="outline-none">
                <Card>
                  <CardHeader>
                    <CardTitle>{t("bookableItemDetails.rules")}</CardTitle>
                    {active && canEdit ? (
                      <CardAction className="flex gap-3">
                        {editing ? (
                          <>
                            <Button
                              key="save"
                              ref={saveButtonRef}
                              type="submit"
                              size="sm"
                              form={formId}
                              disabled={updateMutation.isPending || rulesSaveBlocked}
                              aria-busy={updateMutation.isPending}
                            >
                              <SaveIcon aria-hidden="true" />
                              {t("bookableItems.actions.save")}
                            </Button>
                            <Button
                              type="button"
                              size="sm"
                              variant="ghost"
                              disabled={updateMutation.isPending}
                              onClick={cancelEdit}
                            >
                              <XIcon aria-hidden="true" />
                              {t("bookableItemDetails.cancelEdit")}
                            </Button>
                          </>
                        ) : (
                          <Button
                            key="edit"
                            ref={editButtonRef}
                            type="button"
                            size="sm"
                            variant="ghost"
                            onClick={() => setEdit(true)}
                          >
                            <PencilIcon aria-hidden="true" />
                            {t("bookableItemDetails.edit")}
                          </Button>
                        )}
                      </CardAction>
                    ) : null}
                  </CardHeader>
                  <CardContent>
                    {editing ? (
                      <BookableItemConfigurationForm
                        configuration={configuration}
                        globalId={globalId}
                        formId={formId}
                        pending={updateMutation.isPending}
                        staleEdit={staleEdit}
                        conflictVersion={conflictVersion}
                        failed={updateMutation.isError}
                        onSubmit={(input, version) => updateMutation.mutateAsync({ input, version })}
                        onDiscardConflictedDraft={discardConflictedDraft}
                        onSaveBlockedChange={setRulesSaveBlocked}
                      />
                    ) : (
                      <BookableItemRulesReadOut configuration={configuration} />
                    )}
                  </CardContent>
                </Card>
              </Tabs.Panel>

              {configuration.capabilities.canViewAudit ? (
                <Tabs.Panel value="audit" className="outline-none">
                  <BookableItemAuditLog configurationId={configuration.id} />
                </Tabs.Panel>
              ) : null}

              <Tabs.Panel value="access" className="outline-none">
                <Card>
                  <CardContent className="pt-0">
                    <InventoryAccessReadOnly instrumentId={target.value.id} />
                  </CardContent>
                </Card>
              </Tabs.Panel>
            </div>
            <div className="min-w-0 space-y-6 @2xl:sticky @2xl:top-4 @2xl:self-start">
              <BookableItemFactsAside configuration={configuration} displayTimeZone={preferences.timeZone} />
              {configuration.capabilities.canManageNotificationSubscription ? (
                <BookableItemNotificationSubscription
                  key={`${currentUser.id}-${configuration.id}-${configuration.capabilities.canManageNotificationSubscription}`}
                  configurationId={configuration.id}
                  globalId={globalId}
                  subjectId={currentUser.id}
                />
              ) : null}
            </div>
          </div>
        </Tabs.Root>
      </div>

      <AlertDialog open={archiveOpen} onOpenChange={(open) => !archiveMutation.isPending && setArchiveOpen(open)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookableItemDetails.archiveDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("bookableItemDetails.archiveDialog.description", { item: target.value.name })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          {archiveMutation.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t("bookableItemDetails.archiveDialog.error")}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={archiveMutation.isPending}>{commonT("actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={archiveMutation.isPending}
              aria-busy={archiveMutation.isPending}
              onClick={() => archiveMutation.mutate()}
            >
              {t("bookableItemDetails.archiveDialog.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <AlertDialog
        open={permanentDeleteOpen}
        onOpenChange={(open) => {
          if (permanentDeleteMutation.isPending) return;
          setPermanentDeleteOpen(open);
          if (!open) requestAnimationFrame(() => actionsButtonRef.current?.focus());
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookableItemDetails.permanentDeleteDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("bookableItemDetails.permanentDeleteDialog.description", { item: target.value.name })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <label htmlFor={permanentConfirmationId} className="space-y-2 text-sm">
            <span>{t("bookableItemDetails.permanentDeleteDialog.confirmationLabel")}</span>
            <Input
              id={permanentConfirmationId}
              value={permanentDeleteConfirmation}
              onChange={(event) => setPermanentDeleteConfirmation(event.currentTarget.value)}
              autoComplete="off"
            />
          </label>
          {permanentDeleteMutation.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t(
                lifecycleErrorKey(
                  permanentDeleteMutation.error,
                  "bookableItemDetails.permanentDeleteDialog.error",
                  "bookableItemDetails.lifecycleErrors.stale",
                  "bookableItemDetails.lifecycleErrors.stateChanged",
                ),
              )}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={permanentDeleteMutation.isPending}>
              {commonT("actions.cancel")}
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={permanentDeleteMutation.isPending || permanentDeleteConfirmation !== target.value.name}
              aria-busy={permanentDeleteMutation.isPending}
              onClick={() => permanentDeleteMutation.mutate()}
            >
              {t("bookableItemDetails.permanentDeleteDialog.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <AlertDialog open={leaveOpen} onOpenChange={(open) => !leaveMutation.isPending && setLeaveOpen(open)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookableItemDetails.leaveDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>{t("bookableItemDetails.leaveDialog.description")}</AlertDialogDescription>
          </AlertDialogHeader>
          {leaveMutation.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t("bookableItemDetails.leaveDialog.error")}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={leaveMutation.isPending}>{commonT("actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={leaveMutation.isPending}
              aria-busy={leaveMutation.isPending}
              onClick={() => leaveMutation.mutate()}
            >
              {t("bookableItemDetails.actions.leave")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <p role="status" aria-live="polite" className="sr-only">
        {updateMutation.isPending
          ? t("bookableItemDetails.update.pending")
          : saveAnnouncement === "archived"
            ? t("bookableItemDetails.update.archived")
            : saveAnnouncement === "restored"
              ? t("bookableItemDetails.update.restored")
              : null}
      </p>
    </main>
  );
}
