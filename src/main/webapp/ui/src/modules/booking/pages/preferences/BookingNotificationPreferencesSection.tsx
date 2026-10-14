import { Form, isDirty, reset, useField, useForm } from "@formisch/react";
import { useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { CheckIcon } from "lucide-react";
import { type Ref, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { bookingSubscriptionKeys } from "@/modules/booking/domain/bookingKeys";
import {
  type BookingNotificationPreferencesInput,
  bookingNotificationPreferencesQuery,
  bookingNotificationPreferencesQueryKey,
  unsubscribeFromAllBookingNotificationSubscriptions,
  useReplaceBookingNotificationPreferences,
} from "@/modules/booking/domain/bookingNotificationSubscriptions";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useApiV2Mutation } from "@/modules/common/queries/apiV2Mutation";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Button } from "@/modules/common/ui/button";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Field, FieldLabel } from "@/modules/common/ui/field";
import { Switch } from "@/modules/common/ui/switch";
import { Heading } from "@/modules/common/ui/typography";
import {
  BookingNotificationChoiceFields,
  BookingNotificationChoiceSchema,
  bookingNotificationChoice,
} from "./BookingNotificationChoiceFields";

const NotificationPreferencesFormSchema = v.object({
  notifyOwnActions: v.boolean(),
  notifyOnCreated: v.boolean(),
  notifyOnCancelled: v.boolean(),
  ...BookingNotificationChoiceSchema.entries,
});

type NotificationPreferencesForm = v.InferOutput<typeof NotificationPreferencesFormSchema>;

function formInput(preferences: BookingNotificationPreferencesInput): NotificationPreferencesForm {
  return {
    notifyOwnActions: preferences.notifyOwnActions,
    notifyOnCreated: preferences.notifyOnCreated,
    notifyOnCancelled: preferences.notifyOnCancelled,
    ...bookingNotificationChoice(preferences.autoSubscribeOwnedItems),
  };
}

function NotificationToggle({
  id,
  label,
  checked,
  disabled,
  inputRef,
  onCheckedChange,
}: {
  id: string;
  label: string;
  checked: boolean;
  disabled: boolean;
  inputRef: Ref<HTMLInputElement>;
  onCheckedChange: (checked: boolean) => void;
}) {
  return (
    <Field orientation="horizontal" data-disabled={disabled}>
      <Checkbox
        id={id}
        className="rounded-sm"
        checked={checked}
        disabled={disabled}
        inputRef={inputRef}
        onCheckedChange={onCheckedChange}
      />
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
    </Field>
  );
}

export function BookingNotificationPreferencesSection() {
  const { data: currentUser } = useCurrentUserQuery();
  return <BookingNotificationPreferencesForUser key={currentUser.id} subjectId={currentUser.id} />;
}

function BookingNotificationPreferencesForUser({ subjectId }: { subjectId: number }) {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const _queryClient = useQueryClient();
  const _queryKey = bookingNotificationPreferencesQueryKey(subjectId);
  const preferences = useSuspenseQuery({
    ...bookingNotificationPreferencesQuery(subjectId, token),
  }).data;
  const form = useForm({
    schema: NotificationPreferencesFormSchema,
    initialInput: formInput(preferences),
  });
  const notifyOwnActions = useField(form, { path: ["notifyOwnActions"] });
  const notifyOnCreated = useField(form, { path: ["notifyOnCreated"] });
  const notifyOnCancelled = useField(form, { path: ["notifyOnCancelled"] });
  const replace = useReplaceBookingNotificationPreferences(subjectId);
  const [unsubscribeCount, setUnsubscribeCount] = useState<number>();
  const unsubscribeAll = useApiV2Mutation({
    meta: { invalidates: [bookingSubscriptionKeys.notifications(subjectId)] },
    mutationFn: () => unsubscribeFromAllBookingNotificationSubscriptions(token),
    onSuccess: async (count) => {
      setUnsubscribeCount(count);
    },
  });
  const dirty = isDirty(form);
  const pending = replace.isPending;
  const saved = replace.isSuccess && !dirty;

  useEffect(() => {
    if (!dirty) {
      reset(form, {
        initialInput: formInput(preferences),
      });
    }
  }, [
    dirty,
    form,
    preferences.autoSubscribeOwnedItems,
    preferences.notifyOwnActions,
    preferences.notifyOnCreated,
    preferences.notifyOnCancelled,
  ]);

  return (
    <section className="max-w-2xl space-y-4" aria-labelledby="booking-notifications-heading">
      <Heading level={4} as="h2" id="booking-notifications-heading">
        {t("notificationSubscriptions.preferences.title")}
      </Heading>
      <Form
        of={form}
        className="space-y-4 rounded-sm border p-4"
        onSubmit={(input) => {
          replace.reset();
          replace.mutate(
            {
              autoSubscribeOwnedItems: input.choice === "ON",
              notifyOwnActions: input.notifyOwnActions,
              notifyOnCreated: input.notifyOnCreated,
              notifyOnCancelled: input.notifyOnCancelled,
            },
            {
              onSuccess: (saved) => reset(form, { initialInput: formInput(saved) }),
            },
          );
        }}
      >
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">
            {t("notificationSubscriptions.preferences.events.description")}
          </p>
          <NotificationToggle
            id="booking-notify-on-created"
            label={t("notificationSubscriptions.preferences.events.created")}
            checked={notifyOnCreated.input === true}
            disabled={pending}
            inputRef={notifyOnCreated.props.ref}
            onCheckedChange={notifyOnCreated.onChange}
          />
          <NotificationToggle
            id="booking-notify-on-cancelled"
            label={t("notificationSubscriptions.preferences.events.cancelled")}
            checked={notifyOnCancelled.input === true}
            disabled={pending}
            inputRef={notifyOnCancelled.props.ref}
            onCheckedChange={notifyOnCancelled.onChange}
          />
        </div>
        <BookingNotificationChoiceFields
          form={form}
          labelKey="notificationSubscriptions.preferences.autoSubscribe.label"
          descriptionKey="notificationSubscriptions.preferences.autoSubscribe.description"
          disabled={pending}
        />
        <p className="text-sm text-muted-foreground">
          {preferences.emailDelivery
            ? t("notificationSubscriptions.preferences.emailDelivery.on")
            : t("notificationSubscriptions.preferences.emailDelivery.off")}{" "}
          <a className="underline underline-offset-4" href="/userform#prefContainer">
            {t("notificationSubscriptions.preferences.emailDelivery.change")}
          </a>
        </p>
        {replace.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("notificationSubscriptions.preferences.saveError")}
          </p>
        ) : null}
        <div className="space-y-2">
          <label htmlFor="notify-own-actions">{t("notificationSubscriptions.preferences.ownActions.label")}</label>
          <Switch
            id="notify-own-actions"
            checked={notifyOwnActions.input ?? false}
            disabled={pending}
            aria-describedby="notify-own-actions-description"
            onCheckedChange={(checked) => notifyOwnActions.onChange(checked)}
          />
          <p id="notify-own-actions-description">{t("notificationSubscriptions.preferences.ownActions.description")}</p>
        </div>
        <Button
          type="submit"
          size="sm"
          disabled={pending || !dirty}
          aria-busy={pending}
          variant={saved ? "saved" : "default"}
        >
          {saved ? <CheckIcon aria-hidden="true" /> : null}
          {saved
            ? t("preferences.actions.saved")
            : pending
              ? t("notificationSubscriptions.preferences.saving")
              : t("notificationSubscriptions.preferences.save")}
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={pending || !dirty}
          onClick={() => {
            reset(form, { initialInput: formInput(preferences) });
            replace.reset();
          }}
        >
          {t("notificationSubscriptions.preferences.cancel")}
        </Button>
        <p role="status" className="sr-only">
          {saved ? t("preferences.actions.saved") : null}
        </p>
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
        <p role="status" className={unsubscribeCount !== undefined ? "text-sm text-muted-foreground" : "sr-only"}>
          {unsubscribeCount !== undefined
            ? t("notificationSubscriptions.preferences.unsubscribed", { count: unsubscribeCount })
            : null}
        </p>
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
