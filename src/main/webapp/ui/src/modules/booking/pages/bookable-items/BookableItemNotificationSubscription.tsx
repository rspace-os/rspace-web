import { Form, isDirty, reset, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { BellIcon, CheckIcon, SaveIcon, XIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { bookingNotificationSubscriptionQuery } from "@/modules/booking/domain/bookingNotificationSubscriptions";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import {
  getGetMyBookingNotificationSubscriptionQueryKey,
  invalidateGetMyBookingNotificationSubscription,
  invalidateListBookingConfigurations,
  useReplaceMyBookingNotificationSubscription,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { BookingNotificationSubscriptionStatus } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionStatus";
import { QueryBoundary } from "@/modules/common/queries/QueryBoundary";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Skeleton } from "@/modules/common/ui/skeleton";
import {
  BookingNotificationChoiceFields,
  BookingNotificationChoiceSchema,
  bookingNotificationChoice,
} from "../preferences/BookingNotificationChoiceFields";

type SubscriptionResponse = { body: BookingNotificationSubscriptionStatus; headers: Headers };
function hasNotificationState(status: SubscriptionResponse | undefined): status is SubscriptionResponse & {
  body: BookingNotificationSubscriptionStatus & { enabled: boolean };
} {
  return typeof status?.body.enabled === "boolean" && status.headers.has("ETag");
}
function etag(status: SubscriptionResponse): string {
  const value = status.headers.get("ETag");
  if (!value) throw new Error("Notification subscription validator is missing");
  return value;
}

function ItemNotificationEditor({
  configurationId,
  subscription,
  feedback,
  setFeedback,
}: {
  configurationId: number;
  globalId: string;
  subscription: SubscriptionResponse & {
    body: BookingNotificationSubscriptionStatus & { enabled: boolean };
  };
  feedback: "saved" | "conflict" | "saveError" | null;
  setFeedback: (value: "saved" | "conflict" | "saveError" | null) => void;
}) {
  const { t } = useTranslation("booking");
  const queryClient = useQueryClient();
  const queryKey = getGetMyBookingNotificationSubscriptionQueryKey(configurationId);
  const form = useForm({
    schema: BookingNotificationChoiceSchema,
    initialInput: bookingNotificationChoice(subscription.body.enabled),
  });
  const subscriptionEtag = etag(subscription);
  const [baselineEtag, setBaselineEtag] = useState(subscriptionEtag);
  const dirty = isDirty(form);
  useEffect(() => {
    if (!dirty) {
      reset(form, { initialInput: bookingNotificationChoice(subscription.body.enabled) });
      setBaselineEtag(subscriptionEtag);
    }
  }, [dirty, form, subscription.body.enabled, subscriptionEtag]);
  const replaceOperation = useReplaceMyBookingNotificationSubscription();
  const mutation = useMutation({
    mutationFn: async ({ enabled, validator }: { enabled: boolean; validator: string }) => {
      const saved = await replaceOperation.mutateAsync({
        id: configurationId,
        data: { enabled },
        headers: { "If-Match": validator },
      });
      if (!hasNotificationState(saved)) throw new Error("Notification subscription state is missing");
      return saved;
    },
    onSuccess: async (saved) => {
      queryClient.setQueryData(queryKey, saved);
      await invalidateGetMyBookingNotificationSubscription(queryClient, configurationId);
      reset(form, { initialInput: bookingNotificationChoice(saved.body.enabled) });
      setBaselineEtag(etag(saved));
      setFeedback("saved");
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 412) {
        setFeedback("conflict");
        const latest = await queryClient.fetchQuery({
          ...bookingNotificationSubscriptionQuery(configurationId),
          staleTime: 0,
        });
        if (!hasNotificationState(latest)) {
          setFeedback("saveError");
          return;
        }
        reset(form, { initialInput: bookingNotificationChoice(latest.body.enabled) });
        setBaselineEtag(etag(latest));
        return;
      }
      if (error instanceof ApiV2ProblemError && (error.status === 403 || error.status === 404)) {
        await Promise.all([
          invalidateGetMyBookingNotificationSubscription(queryClient, configurationId),
          invalidateListBookingConfigurations(queryClient),
        ]);
      }
      setFeedback("saveError");
    },
  });
  const saved = feedback === "saved" && !dirty;
  return (
    <Card size="sm" aria-labelledby="item-notification-subscription-heading">
      <CardHeader>
        <CardTitle id="item-notification-subscription-heading" className="flex items-center gap-2">
          <BellIcon aria-hidden="true" className="size-4" />
          {t("notificationSubscriptions.item.title")}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <Form
          of={form}
          className="space-y-3"
          onChange={() => {
            if (feedback === "saved") setFeedback(null);
          }}
          onSubmit={(input) => {
            setFeedback(null);
            mutation.mutate({ enabled: input.choice === "ON", validator: baselineEtag });
          }}
        >
          <BookingNotificationChoiceFields
            form={form}
            labelKey="notificationSubscriptions.item.label"
            disabled={mutation.isPending}
          />
          {feedback === "conflict" ? (
            <p role="alert" className="text-sm text-destructive">
              {t("notificationSubscriptions.item.conflict")}
            </p>
          ) : feedback === "saveError" ? (
            <p role="alert" className="text-sm text-destructive">
              {t("notificationSubscriptions.item.saveError")}
            </p>
          ) : null}
          <div className="flex flex-wrap gap-3">
            <Button
              type="submit"
              size="sm"
              disabled={mutation.isPending || !dirty}
              aria-busy={mutation.isPending}
              className={
                saved
                  ? "bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-100 dark:bg-emerald-500 dark:hover:bg-emerald-400"
                  : undefined
              }
            >
              {saved ? <CheckIcon aria-hidden="true" /> : <SaveIcon aria-hidden="true" />}
              {saved ? t("preferences.actions.saved") : t("preferences.actions.save")}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={mutation.isPending || !dirty}
              onClick={() => {
                reset(form, { initialInput: bookingNotificationChoice(subscription.body.enabled) });
                setBaselineEtag(subscriptionEtag);
                setFeedback(null);
              }}
            >
              <XIcon aria-hidden="true" />
              {t("bookableItemDetails.cancelEdit")}
            </Button>
          </div>
        </Form>
      </CardContent>
    </Card>
  );
}

function ItemNotificationCardHeader() {
  const { t } = useTranslation("booking");
  return (
    <CardHeader>
      <CardTitle>{t("notificationSubscriptions.item.title")}</CardTitle>
    </CardHeader>
  );
}

function ItemNotificationLoadError({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation("booking");
  return (
    <Card size="sm">
      <ItemNotificationCardHeader />
      <CardContent className="space-y-3">
        <p role="alert" className="text-sm text-destructive">
          {t("notificationSubscriptions.item.loadError")}
        </p>
        <Button type="button" size="sm" variant="outline" onClick={onRetry}>
          {t("notificationSubscriptions.item.retry")}
        </Button>
      </CardContent>
    </Card>
  );
}

function ItemNotificationSubscription({ configurationId, globalId }: { configurationId: number; globalId: string }) {
  const [feedback, setFeedback] = useState<"saved" | "conflict" | "saveError" | null>(null);
  const subscription = useSuspenseQuery(bookingNotificationSubscriptionQuery(configurationId));

  if (!hasNotificationState(subscription.data)) {
    return <ItemNotificationLoadError onRetry={() => void subscription.refetch()} />;
  }

  return (
    <ItemNotificationEditor
      configurationId={configurationId}
      globalId={globalId}
      subscription={subscription.data}
      feedback={feedback}
      setFeedback={setFeedback}
    />
  );
}

export function BookableItemNotificationSubscription({
  configurationId,
  globalId,
}: {
  configurationId: number;
  globalId: string;
}) {
  return (
    <QueryBoundary
      fallback={
        <Card size="sm" aria-busy="true">
          <ItemNotificationCardHeader />
          <CardContent>
            <Skeleton className="h-24 w-full" />
          </CardContent>
        </Card>
      }
      error={({ retry }) => <ItemNotificationLoadError onRetry={retry} />}
    >
      <ItemNotificationSubscription configurationId={configurationId} globalId={globalId} />
    </QueryBoundary>
  );
}
