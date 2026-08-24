import { Form, useForm } from "@formisch/react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import {
  loadBookingSettings,
  type SchedulingSettings,
  SchedulingSettingsFields,
  SchedulingSettingsSchema,
  saveBookingSettings,
} from "./schedulingSettings";

export default function BookingSettingsPage() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const settings = useSuspenseQuery({
    queryKey: ["api-v2", "booking-settings"],
    queryFn: ({ signal }) => loadBookingSettings(token, signal),
  }).data;
  const form = useForm({ schema: SchedulingSettingsSchema, initialInput: settings });
  const mutation = useMutation({
    mutationFn: (input: SchedulingSettings) => saveBookingSettings(input, settings.configurationVersion, token),
    onSuccess: (saved) => queryClient.setQueryData(["api-v2", "booking-settings"], saved),
  });

  return (
    <main className="p-4 sm:p-8">
      <Heading level={2} as="h1" className="mb-2">
        {t("settings.title")}
      </Heading>
      <p className="mb-5 text-sm text-muted-foreground">{t("settings.description")}</p>
      <Separator className="mb-8 h-px bg-gray-300" />
      <Form of={form} className="max-w-2xl space-y-8" onSubmit={(input) => mutation.mutateAsync(input)}>
        <SchedulingSettingsFields form={form} disabled={mutation.isPending} />
        {mutation.isError ? (
          <FieldError>
            {t(
              mutation.error instanceof ApiV2ProblemError &&
                mutation.error.code === "errors.api.v2.bookingConfiguration.stale"
                ? "settings.errors.stale"
                : "settings.errors.save",
            )}
          </FieldError>
        ) : null}
        {mutation.isSuccess ? <p role="status">{t("settings.saved")}</p> : null}
        <Button type="submit" disabled={mutation.isPending} aria-busy={mutation.isPending}>
          {t("settings.actions.save")}
        </Button>
      </Form>
    </main>
  );
}
