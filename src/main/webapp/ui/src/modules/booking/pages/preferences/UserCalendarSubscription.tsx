import { faApple } from "@fortawesome/free-brands-svg-icons/faApple";
import { faGoogle } from "@fortawesome/free-brands-svg-icons/faGoogle";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarIcon, CheckIcon, CopyIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import {
  calendarApplicationUrls,
  createUserCalendarSubscription,
  fetchUserCalendarSubscriptionStatus,
  revokeUserCalendarSubscription,
  rotateUserCalendarSubscription,
  userCalendarSubscriptionQueryKey,
} from "@/modules/booking/pages/bookable-items/bookableItemCalendarSubscription";
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
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/modules/common/ui/input-group";
import { Skeleton } from "@/modules/common/ui/skeleton";

export function UserCalendarSubscription({ token }: { token: string }) {
  const { t } = useTranslation("booking");
  const queryClient = useQueryClient();
  const [copied, setCopied] = useState(false);
  const [clipboardError, setClipboardError] = useState(false);
  const [confirmingReplace, setConfirmingReplace] = useState(false);
  const status = useQuery({
    queryKey: userCalendarSubscriptionQueryKey,
    queryFn: ({ signal }) => fetchUserCalendarSubscriptionStatus(token, signal),
    retry: false,
  });
  const resetCopy = () => {
    setCopied(false);
    setClipboardError(false);
  };
  const create = useMutation({
    mutationFn: () => createUserCalendarSubscription(token),
    retry: false,
    onMutate: () => {
      resetCopy();
      // A fresh link supersedes any earlier replace conflict.
      rotate.reset();
    },
    onSuccess: (created) => queryClient.setQueryData(userCalendarSubscriptionQueryKey, created),
  });
  const rotate = useMutation({
    mutationFn: () => {
      if (status.data === undefined) throw new Error("Calendar subscription status is unavailable");
      return rotateUserCalendarSubscription(token, status.data.etag);
    },
    retry: false,
    onMutate: resetCopy,
    onSuccess: (rotated) => queryClient.setQueryData(userCalendarSubscriptionQueryKey, rotated),
    onError: (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 409) {
        void queryClient.invalidateQueries({ queryKey: userCalendarSubscriptionQueryKey });
      }
    },
    onSettled: () => setConfirmingReplace(false),
  });
  const revoke = useMutation({
    mutationFn: () => revokeUserCalendarSubscription(token),
    retry: false,
    onSuccess: () => {
      resetCopy();
      rotate.reset();
      void queryClient.invalidateQueries({ queryKey: userCalendarSubscriptionQueryKey });
    },
  });
  const subscriptionUrl = status.data?.subscriptionUrl ?? null;
  const pending = create.isPending || rotate.isPending || revoke.isPending;
  const rotateConflict = rotate.error instanceof ApiV2ProblemError && rotate.error.status === 409;

  const copyLink = async () => {
    if (subscriptionUrl === null) return;
    resetCopy();
    try {
      await navigator.clipboard.writeText(subscriptionUrl);
      setCopied(true);
    } catch {
      setClipboardError(true);
    }
  };

  return (
    <section className="max-w-2xl space-y-4" aria-labelledby="user-calendar-subscription-heading">
      <div className="space-y-1">
        <h2 id="user-calendar-subscription-heading" className="text-lg font-semibold">
          {t("preferences.calendarSubscription.title")}
        </h2>
        <p className="text-sm text-muted-foreground">{t("preferences.calendarSubscription.description")}</p>
      </div>
      {status.isPending ? (
        <div aria-busy="true">
          <p role="status" className="sr-only">
            {t("preferences.calendarSubscription.loading")}
          </p>
          <Skeleton aria-hidden="true" className="h-9 w-full" />
        </div>
      ) : null}
      {status.isError ? (
        <div className="space-y-3">
          <p role="alert" className="text-sm text-destructive">
            {t("preferences.calendarSubscription.statusError")}
          </p>
          <Button type="button" variant="outline" onClick={() => void status.refetch()}>
            {t("preferences.calendarSubscription.retry")}
          </Button>
        </div>
      ) : null}
      {status.isSuccess && subscriptionUrl === null ? (
        <Button type="button" disabled={pending} aria-busy={create.isPending} onClick={() => create.mutate()}>
          {t("preferences.calendarSubscription.create")}
        </Button>
      ) : null}
      {subscriptionUrl !== null ? (
        <div className="space-y-4">
          <CalendarApplicationLinks subscriptionUrl={subscriptionUrl} />
          <div className="space-y-2">
            <label htmlFor="user-booking-calendar-url" className="text-sm font-medium">
              {t("preferences.calendarSubscription.copyPrompt")}
            </label>
            <InputGroup>
              <InputGroupInput
                id="user-booking-calendar-url"
                readOnly
                value={subscriptionUrl}
                className="font-mono text-xs"
              />
              <InputGroupAddon align="inline-end">
                <InputGroupButton
                  size="icon-xs"
                  aria-label={t("preferences.calendarSubscription.copy")}
                  onClick={() => void copyLink()}
                >
                  {copied ? <CheckIcon aria-hidden="true" /> : <CopyIcon aria-hidden="true" />}
                </InputGroupButton>
              </InputGroupAddon>
            </InputGroup>
            {copied ? <p role="status">{t("preferences.calendarSubscription.copied")}</p> : null}
            {clipboardError ? (
              <p role="alert" className="text-sm text-destructive">
                {t("preferences.calendarSubscription.copyError")}
              </p>
            ) : null}
          </div>
          {rotateConflict ? (
            <p role="alert" className="text-sm">
              {t("preferences.calendarSubscription.replaceConflict")}
            </p>
          ) : rotate.isError || revoke.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t("preferences.calendarSubscription.changeError")}
            </p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={pending}
              aria-busy={rotate.isPending}
              onClick={() => {
                rotate.reset();
                setConfirmingReplace(true);
              }}
            >
              {t("preferences.calendarSubscription.replace")}
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={pending}
              aria-busy={revoke.isPending}
              onClick={() => revoke.mutate()}
            >
              {t("preferences.calendarSubscription.revoke")}
            </Button>
          </div>
        </div>
      ) : null}
      {create.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {t("preferences.calendarSubscription.createError")}
        </p>
      ) : null}
      <AlertDialog
        open={confirmingReplace}
        onOpenChange={(open) => !open && !rotate.isPending && setConfirmingReplace(false)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("preferences.calendarSubscription.replaceDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("preferences.calendarSubscription.replaceDialog.description")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={rotate.isPending}>
              {t("preferences.calendarSubscription.replaceDialog.cancel")}
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={rotate.isPending}
              aria-busy={rotate.isPending}
              onClick={() => rotate.mutate()}
            >
              {t("preferences.calendarSubscription.replaceDialog.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}

function CalendarApplicationLinks({ subscriptionUrl }: { subscriptionUrl: string }) {
  const { t } = useTranslation("booking");
  const apps = calendarApplicationUrls(subscriptionUrl);
  const className = buttonVariants({ variant: "outline" });
  return (
    <div className="flex flex-wrap gap-2">
      <a className={className} href={apps.apple}>
        <FontAwesomeIcon icon={faApple} className="size-4" />
        {t("preferences.calendarSubscription.apple")}
      </a>
      <a className={className} href={apps.google} target="_blank" rel="noreferrer">
        <FontAwesomeIcon icon={faGoogle} className="size-4" />
        {t("preferences.calendarSubscription.google")}
      </a>
      <a className={className} href={apps.other}>
        <CalendarIcon aria-hidden="true" />
        {t("preferences.calendarSubscription.other")}
      </a>
    </div>
  );
}
