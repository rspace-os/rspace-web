import { Form, isDirty, reset, useForm } from "@formisch/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellIcon, CheckIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import {
  type BookingNotificationSubscription,
  bookingNotificationSubscriptionsQueryKey,
  fetchBookingNotificationSubscription,
  replaceBookingNotificationSubscription,
} from "@/modules/booking/domain/bookingNotificationSubscriptions";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Skeleton } from "@/modules/common/ui/skeleton";
import {
  BookingNotificationChoiceFields,
  BookingNotificationChoiceSchema,
  bookingNotificationChoice,
} from "../preferences/BookingNotificationChoiceFields";

function ItemNotificationEditor({
  configurationId,
  globalId,
  token,
  subjectId,
  subscription,
  feedback,
  setFeedback,
}: {
  configurationId: number;
  globalId: string;
  token: string;
  subjectId: number;
  subscription: BookingNotificationSubscription;
  feedback: "saved" | "conflict" | "saveError" | null;
  setFeedback: (value: "saved" | "conflict" | "saveError" | null) => void;
}) {
  const { t } = useTranslation("booking");
  const queryClient = useQueryClient();
  const queryKey = bookingNotificationSubscriptionsQueryKey.item(subjectId, configurationId);
  const form = useForm({
    schema: BookingNotificationChoiceSchema,
    initialInput: bookingNotificationChoice(subscription.enabled),
  });
  const mutation = useMutation({
    mutationFn: ({ enabled, version }: { enabled: boolean; version: number }) =>
      replaceBookingNotificationSubscription(configurationId, enabled, version, token),
    onSuccess: async (saved) => {
      queryClient.setQueryData(queryKey, saved);
      await queryClient.invalidateQueries({ queryKey: bookingNotificationSubscriptionsQueryKey.all(subjectId) });
      reset(form, { initialInput: bookingNotificationChoice(saved.enabled) });
      setFeedback("saved");
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 409) {
        setFeedback("conflict");
        await queryClient.invalidateQueries({ queryKey });
        return;
      }
      if (error instanceof ApiV2ProblemError && (error.status === 403 || error.status === 404)) {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey }),
          queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations", "target", globalId] }),
        ]);
      }
      reset(form, { initialInput: bookingNotificationChoice(subscription.enabled) });
      setFeedback("saveError");
    },
  });
  const dirty = isDirty(form);
  const saved = feedback === "saved" && !dirty;
  const pending = mutation.isPending;

  return (
    <Card size="sm" aria-labelledby="item-notification-subscription-heading">
      <CardHeader>
        <CardTitle id="item-notification-subscription-heading" className="flex items-center gap-2">
          <BellIcon aria-hidden="true" className="size-4" />
          {t("notificationSubscriptions.item.title")}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-1 text-sm text-muted-foreground">
          <p>{t("notificationSubscriptions.item.description")}</p>
          <a href="/userform#prefContainer" className="text-primary underline underline-offset-4">
            {t("notificationSubscriptions.item.profileLink")}
          </a>
        </div>
        <Form
          of={form}
          className="space-y-3"
          onChange={() => {
            if (feedback === "saved") setFeedback(null);
          }}
          onSubmit={(input) => {
            setFeedback(null);
            mutation.mutate({ enabled: input.choice === "ON", version: subscription.version });
          }}
        >
          <BookingNotificationChoiceFields
            form={form}
            labelKey="notificationSubscriptions.item.label"
            disabled={pending}
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
              disabled={pending || !dirty}
              aria-busy={pending}
              className={
                saved
                  ? "bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-100 dark:bg-emerald-500 dark:hover:bg-emerald-400"
                  : undefined
              }
            >
              {saved ? <CheckIcon aria-hidden="true" /> : null}
              {t(saved ? "preferences.actions.saved" : "preferences.actions.save")}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={pending || !dirty}
              onClick={() => {
                reset(form, { initialInput: bookingNotificationChoice(subscription.enabled) });
                setFeedback(null);
              }}
            >
              {t("bookableItemDetails.cancelEdit")}
            </Button>
          </div>
        </Form>
      </CardContent>
    </Card>
  );
}

export function BookableItemNotificationSubscription({
  configurationId,
  globalId,
  canManageNotificationSubscription,
}: {
  configurationId: number;
  globalId: string;
  canManageNotificationSubscription: boolean;
}) {
  const { data: currentUser } = useCurrentUserQuery();
  return (
    <BookableItemNotificationSubscriptionForUser
      key={`${currentUser.id}-${configurationId}-${canManageNotificationSubscription}`}
      configurationId={configurationId}
      globalId={globalId}
      canManageNotificationSubscription={canManageNotificationSubscription}
      subjectId={currentUser.id}
    />
  );
}

function BookableItemNotificationSubscriptionForUser({
  configurationId,
  globalId,
  canManageNotificationSubscription,
  subjectId,
}: {
  configurationId: number;
  globalId: string;
  canManageNotificationSubscription: boolean;
  subjectId: number;
}) {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const [feedback, setFeedback] = useState<"saved" | "conflict" | "saveError" | null>(null);
  const queryKey = bookingNotificationSubscriptionsQueryKey.item(subjectId, configurationId);
  const subscription = useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchBookingNotificationSubscription(configurationId, token, signal),
    enabled: canManageNotificationSubscription,
  });

  if (!canManageNotificationSubscription) return null;
  if (subscription.isPending) {
    return (
      <Card size="sm" aria-busy="true">
        <CardHeader>
          <CardTitle>{t("notificationSubscriptions.item.title")}</CardTitle>
        </CardHeader>
        <CardContent>
          <Skeleton className="h-24 w-full" />
        </CardContent>
      </Card>
    );
  }
  if (subscription.isError) {
    return (
      <Card size="sm">
        <CardHeader>
          <CardTitle>{t("notificationSubscriptions.item.title")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p role="alert" className="text-sm text-destructive">
            {t("notificationSubscriptions.item.loadError")}
          </p>
          <Button type="button" size="sm" variant="outline" onClick={() => void subscription.refetch()}>
            {t("notificationSubscriptions.item.retry")}
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <ItemNotificationEditor
      key={`${subjectId}-${configurationId}-${subscription.data.version}-${subscription.data.enabled}`}
      subjectId={subjectId}
      configurationId={configurationId}
      globalId={globalId}
      token={token}
      subscription={subscription.data}
      feedback={feedback}
      setFeedback={setFeedback}
    />
  );
}
