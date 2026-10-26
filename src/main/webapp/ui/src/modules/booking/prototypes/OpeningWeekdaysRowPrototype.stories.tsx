// Canonical replacement for the retired shared-hours prototype.
import { Form, reset, useForm } from "@formisch/react";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { openingHoursFacts } from "@/modules/booking/configuration/openingHoursFacts";
import {
  DEFAULT_SCHEDULING_SETTINGS,
  type SchedulingSettings,
  SchedulingSettingsFields,
} from "@/modules/booking/configuration/schedulingSettings";
import {
  BookingConfigurationUpdateInputSchema,
  bookingConfigurationFields,
} from "@/modules/booking/pages/bookable-items/bookingConfiguration";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Button } from "@/modules/common/ui/button";

const INITIAL: SchedulingSettings = {
  ...DEFAULT_SCHEDULING_SETTINGS,
  openingHours: DEFAULT_SCHEDULING_SETTINGS.openingHours.map((entry) => ({ ...entry, start: "09:00", end: "17:00" })),
  slotGranularityMinutes: 15,
  maxBookingDurationMinutes: 240,
};
const fields = bookingConfigurationFields.filter((field) => field.name !== "target" && field.name !== "timezone");

function ConfigurationPrototype({ initialInput = INITIAL }: { initialInput?: SchedulingSettings }) {
  const { t } = useTranslation("booking");
  const [saved, setSaved] = useState(initialInput);
  const [generation, setGeneration] = useState(0);
  const form = useForm({
    schema: BookingConfigurationUpdateInputSchema,
    initialInput: { enabled: true, ...initialInput },
  });
  return (
    <main className="mx-auto max-w-3xl space-y-8 p-6">
      <Form
        of={form}
        className="space-y-4"
        onSubmit={(input) => {
          setSaved(input);
          reset(form, { initialInput: input });
          setGeneration((value) => value + 1);
        }}
      >
        <RenderFields fields={fields} form={form} layout="inline" />
        <SchedulingSettingsFields key={generation} form={form} layout="inline" />
        <div className="flex gap-2">
          <Button type="submit">{t("settings.actions.save")}</Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              reset(form, { initialInput: { enabled: true, ...saved } });
              setGeneration((value) => value + 1);
            }}
          >
            {t("bookableItemDetails.cancelEdit")}
          </Button>
        </div>
      </Form>
      <dl className="space-y-2">
        {openingHoursFacts(saved, t).map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/Opening weekdays/Codex",
  component: ConfigurationPrototype,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof ConfigurationPrototype>;
export default meta;
type Story = StoryObj<typeof meta>;
export const ConfigurationEdit: Story = {};
export const DifferentHoursOnSomeDays: Story = {
  args: {
    initialInput: {
      ...INITIAL,
      openingHours: INITIAL.openingHours.map((entry) => ({
        ...entry,
        start: entry.dayOfWeek === 6 ? "10:00" : "09:00",
        end: entry.dayOfWeek === 5 ? "14:00" : entry.dayOfWeek === 6 ? "16:00" : "17:00",
      })),
    },
  },
};
