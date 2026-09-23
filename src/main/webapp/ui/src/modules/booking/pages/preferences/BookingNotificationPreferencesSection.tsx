import { Form, isDirty, reset, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  bookingNotificationPreferencesQueryKey,
  bookingNotificationSubscriptionsQueryKey,
  fetchBookingNotificationPreferences,
  unsubscribeFromAllBookingNotificationSubscriptions,
  useReplaceBookingNotificationPreferences,
} from "@/modules/booking/domain/bookingNotificationSubscriptions";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Button } from "@/modules/common/ui/button";
import { Heading } from "@/modules/common/ui/typography";
import {
  BookingNotificationChoiceFields,
  BookingNotificationChoiceSchema,
  bookingNotificationChoice,
} from "./BookingNotificationChoiceFields";

export function BookingNotificationPreferencesSection() {
  const { data: currentUser } = useCurrentUserQuery();
  return <BookingNotificationPreferencesForUser key={currentUser.id} subjectId={currentUser.id} />;
}

function BookingNotificationPreferencesForUser({ subjectId }: { subjectId: number }) {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const queryKey = bookingNotificationPreferencesQueryKey(subjectId);
  const preferences = useSuspenseQuery({
    queryKey,
    queryFn: ({ signal }) => fetchBookingNotificationPreferences(token, signal),
  }).data;
  const form = useForm({
    schema: BookingNotificationChoiceSchema,
    initialInput: bookingNotificationChoice(preferences.autoSubscribeOwnedItems),
  });
  const replace = useReplaceBookingNotificationPreferences(subjectId);
  const [unsubscribeCount, setUnsubscribeCount] = useState<number>();
  const unsubscribeAll = useMutation({
    mutationFn: () => unsubscribeFromAllBookingNotificationSubscriptions(token),
    onSuccess: async (count) => {
      setUnsubscribeCount(count);
      await queryClient.invalidateQueries({ queryKey: bookingNotificationSubscriptionsQueryKey.all(subjectId) });
    },
  });
  const dirty = isDirty(form);
  const pending = replace.isPending;

  useEffect(() => {
    if (!dirty) reset(form, { initialInput: bookingNotificationChoice(preferences.autoSubscribeOwnedItems) });
  }, [dirty, form, preferences.autoSubscribeOwnedItems]);

  return (
    <section className="max-w-2xl space-y-4" aria-labelledby="booking-notifications-heading">
      <Heading level={4} as="h2" id="booking-notifications-heading">
        {t("notificationSubscriptions.preferences.title")}
      </Heading>
      <Form
        of={form}
        className="space-y-3 rounded-sm border p-4"
        onSubmit={(input) => {
          replace.reset();
          replace.mutate(
            { autoSubscribeOwnedItems: input.choice === "ON" },
            {
              onSuccess: (saved) =>
                reset(form, { initialInput: bookingNotificationChoice(saved.autoSubscribeOwnedItems) }),
              onError: () =>
                reset(form, { initialInput: bookingNotificationChoice(preferences.autoSubscribeOwnedItems) }),
            },
          );
        }}
      >
        <BookingNotificationChoiceFields
          form={form}
          labelKey="notificationSubscriptions.preferences.autoSubscribe.label"
          descriptionKey="notificationSubscriptions.preferences.autoSubscribe.description"
          disabled={pending}
        />
        {replace.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("notificationSubscriptions.preferences.saveError")}
          </p>
        ) : null}
        {replace.isSuccess && !dirty ? (
          <p role="status" className="text-sm text-muted-foreground">
            {t("notificationSubscriptions.preferences.saved")}
          </p>
        ) : null}
        <Button type="submit" size="sm" disabled={pending || !dirty} aria-busy={pending}>
          {t(pending ? "notificationSubscriptions.preferences.saving" : "notificationSubscriptions.preferences.save")}
        </Button>
      </Form>
      <div className="space-y-3 rounded-sm border p-4">
        <p className="text-sm text-muted-foreground">
          {t("notificationSubscriptions.preferences.existingSubscriptions.description")}
        </p>
        {unsubscribeAll.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("notificationSubscriptions.preferences.unsubscribeError")}
          </p>
        ) : null}
        {unsubscribeCount !== undefined ? (
          <p role="status" className="text-sm text-muted-foreground">
            {t("notificationSubscriptions.preferences.unsubscribed", { count: unsubscribeCount })}
          </p>
        ) : null}
        <Button
          type="button"
          variant="outline"
          disabled={unsubscribeAll.isPending}
          aria-busy={unsubscribeAll.isPending}
          onClick={() => {
            setUnsubscribeCount(undefined);
            unsubscribeAll.mutate();
          }}
        >
          {t("notificationSubscriptions.preferences.unsubscribeAll")}
        </Button>
        <p className="text-sm text-muted-foreground">
          <a className="underline underline-offset-4" href="/booking/all-items">
            {t("notificationSubscriptions.preferences.manageSubscriptions")}
          </a>
        </p>
      </div>
    </section>
  );
}
