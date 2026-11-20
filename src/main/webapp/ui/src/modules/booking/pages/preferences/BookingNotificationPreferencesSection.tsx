import { Form, isDirty, reset, useForm } from "@formisch/react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  bookingNotificationPreferencesQuery,
  useReplaceBookingNotificationPreferences,
} from "@/modules/booking/domain/bookingNotificationSubscriptions";
import { useUnsubscribeMyBookingNotifications } from "@/modules/common/api/v2/generated/users/users";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Button } from "@/modules/common/ui/button";
import { Switch } from "@/modules/common/ui/switch";
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
  const preferences = useSuspenseQuery(bookingNotificationPreferencesQuery(subjectId)).data;
  if (preferences.autoSubscribeOwnedItems === undefined || preferences.notifyOwnActions === undefined) {
    throw new Error("Notification preferences are unavailable");
  }
  const autoSubscribeOwnedItems = preferences.autoSubscribeOwnedItems;
  const form = useForm({
    schema: BookingNotificationChoiceSchema,
    initialInput: bookingNotificationChoice(autoSubscribeOwnedItems),
  });
  const replace = useReplaceBookingNotificationPreferences(subjectId);
  const [unsubscribeCount, setUnsubscribeCount] = useState<number>();
  const unsubscribeAll = useUnsubscribeMyBookingNotifications({
    mutation: {
      onSuccess: (result) => {
        if (result.updatedCount === undefined) throw new Error("Unsubscribe count is unavailable");
        setUnsubscribeCount(result.updatedCount);
      },
    },
  });
  const [ownActions, setOwnActions] = useState<boolean>();
  const notifyOwnActions = ownActions ?? preferences.notifyOwnActions;
  const dirty = isDirty(form) || ownActions !== undefined;
  const pending = replace.isPending;

  useEffect(() => {
    if (!dirty) reset(form, { initialInput: bookingNotificationChoice(autoSubscribeOwnedItems) });
  }, [dirty, form, autoSubscribeOwnedItems]);

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
            { autoSubscribeOwnedItems: input.choice === "ON", notifyOwnActions },
            {
              onSuccess: (saved) => {
                if (saved.autoSubscribeOwnedItems === undefined)
                  throw new Error("Notification preferences are unavailable");
                reset(form, { initialInput: bookingNotificationChoice(saved.autoSubscribeOwnedItems) });
                setOwnActions(undefined);
              },
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
        <div className="space-y-2">
          <label htmlFor="notify-own-actions" className="flex items-center gap-3">
            <Switch
              id="notify-own-actions"
              checked={notifyOwnActions}
              disabled={pending}
              aria-describedby="own-actions-help"
              onCheckedChange={(checked) => {
                replace.reset();
                setOwnActions(checked === preferences.notifyOwnActions ? undefined : checked);
              }}
            />
            {t("notificationSubscriptions.preferences.ownActions.label")}
          </label>
          <p id="own-actions-help" className="text-sm text-muted-foreground">
            {t("notificationSubscriptions.preferences.ownActions.description")}
          </p>
        </div>
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
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={pending || !dirty}
          onClick={() => {
            reset(form, { initialInput: bookingNotificationChoice(autoSubscribeOwnedItems) });
            setOwnActions(undefined);
            replace.reset();
          }}
        >
          {t("notificationSubscriptions.preferences.cancel")}
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
      </div>
    </section>
  );
}
