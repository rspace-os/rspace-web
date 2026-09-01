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
  createOrReplaceUserCalendarSubscription,
  fetchUserCalendarSubscriptionStatus,
  revokeUserCalendarSubscription,
  userCalendarSubscriptionQueryKey,
} from "@/modules/booking/pages/bookable-items/bookableItemCalendarSubscription";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/modules/common/ui/input-group";

export function UserCalendarSubscription({ token }: { token: string }) {
  const { t } = useTranslation("booking");
  const queryClient = useQueryClient();
  const [copied, setCopied] = useState(false);
  const [clipboardError, setClipboardError] = useState(false);
  const status = useQuery({
    queryKey: userCalendarSubscriptionQueryKey,
    queryFn: ({ signal }) => fetchUserCalendarSubscriptionStatus(token, signal),
    retry: false,
  });
  const create = useMutation({
    mutationFn: () => {
      if (status.data === undefined) throw new Error("Calendar subscription status is unavailable");
      return createOrReplaceUserCalendarSubscription(token, status.data.etag);
    },
    retry: false,
    onMutate: () => {
      setCopied(false);
      setClipboardError(false);
    },
    onSuccess: (created) => queryClient.setQueryData(userCalendarSubscriptionQueryKey, created),
    onError: (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 409) {
        void queryClient.invalidateQueries({ queryKey: userCalendarSubscriptionQueryKey });
      }
    },
  });
  const revoke = useMutation({
    mutationFn: () => revokeUserCalendarSubscription(token),
    retry: false,
    onSuccess: () => {
      setCopied(false);
      setClipboardError(false);
      void queryClient.invalidateQueries({ queryKey: userCalendarSubscriptionQueryKey });
    },
  });
  const subscriptionUrl = status.data?.subscriptionUrl ?? null;
  const pending = create.isPending || revoke.isPending;

  const copyLink = async () => {
    if (subscriptionUrl === null) return;
    setCopied(false);
    setClipboardError(false);
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
      {status.isPending ? <p role="status">{t("preferences.calendarSubscription.loading")}</p> : null}
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
          <div className="flex flex-wrap gap-2">
            {(
              [
                ["apple", faApple, t("preferences.calendarSubscription.apple")],
                ["google", faGoogle, t("preferences.calendarSubscription.google")],
              ] as const
            ).map(([application, icon, label]) => (
              <a
                key={application}
                className={buttonVariants({ variant: "outline" })}
                href={calendarApplicationUrls(subscriptionUrl)[application]}
                {...(application === "google" ? { target: "_blank", rel: "noreferrer" } : {})}
              >
                <FontAwesomeIcon icon={icon} className="size-4" />
                {label}
              </a>
            ))}
            <a className={buttonVariants({ variant: "outline" })} href={calendarApplicationUrls(subscriptionUrl).other}>
              <CalendarIcon aria-hidden="true" />
              {t("preferences.calendarSubscription.other")}
            </a>
          </div>
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
          {create.isError || revoke.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t("preferences.calendarSubscription.changeError")}
            </p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={pending}
              aria-busy={create.isPending}
              onClick={() => create.mutate()}
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
      {create.isError && subscriptionUrl === null ? (
        <p role="alert" className="text-sm text-destructive">
          {t("preferences.calendarSubscription.changeError")}
        </p>
      ) : null}
    </section>
  );
}
