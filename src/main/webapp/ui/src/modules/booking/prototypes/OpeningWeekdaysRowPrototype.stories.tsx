// PROTOTYPE ONLY. A single weekday row in the production booking configuration edit workflow.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is not entering the translation catalog. */

import { Form, type FormStore, reset, useField, useForm } from "@formisch/react";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CheckIcon, PencilIcon, Undo2Icon, XIcon } from "lucide-react";
import { type ReactNode, useId, useState } from "react";
import { validOpeningHours } from "@/modules/booking/configuration/schedulingSettings";
import {
  type BookingConfigurationUpdateInput,
  BookingConfigurationUpdateInputSchema,
  bookingConfigurationFields,
} from "@/modules/booking/pages/bookable-items/bookingConfiguration";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Button } from "@/modules/common/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { FieldError } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Heading } from "@/modules/common/ui/typography";

const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const INITIAL: BookingConfigurationUpdateInput = {
  enabled: true,
  openingStart: "09:00",
  openingEnd: "17:00",
  openDays: [1, 2, 3, 4, 5, 6, 7],
  openingExceptions: [],
  slotGranularityMinutes: 15,
  maxBookingDurationMinutes: 240,
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  allowDoubleBooking: false,
};
const fields = bookingConfigurationFields.filter((field) => field.name !== "target" && field.name !== "timezone");

// Mirrors FormField's inline row: the label keeps a 36px line at the top, and the control column is at
// least one 36px line, so single-line controls centre against the label and taller ones grow downward.
const LABEL_CLASS_NAME = "text-sm font-medium @md:flex @md:min-h-9 @md:items-center @md:self-start";
function SettingRow({ label, children }: { label: ReactNode; children: ReactNode }) {
  return (
    <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
      {label}
      <div className="flex min-h-9 min-w-0 flex-col justify-center gap-2">{children}</div>
    </div>
  );
}

// Keep the unrelated production field bindings, without a second opening-hours editor.
function NumericSetting({ form, name, label }: { form: FormStore; name: string; label: string }) {
  const field = useField(form, { path: [name] });
  const id = useId();
  return (
    <SettingRow
      label={
        <label htmlFor={id} className={LABEL_CLASS_NAME}>
          {label}
        </label>
      }
    >
      <Input
        id={id}
        type="number"
        min={0}
        value={typeof field.input === "number" ? field.input : ""}
        onChange={(event) => field.onChange(event.currentTarget.valueAsNumber)}
      />
      {field.errors?.map((error) => (
        <FieldError key={error}>{error}</FieldError>
      ))}
    </SettingRow>
  );
}
function OtherSettings({ form }: { form: FormStore }) {
  const granularity = useField(form, { path: ["slotGranularityMinutes"] });
  const doubleBooking = useField(form, { path: ["allowDoubleBooking"] });
  const id = useId();
  return (
    <>
      <SettingRow
        label={
          <label htmlFor={`${id}-granularity`} className={LABEL_CLASS_NAME}>
            Booking increments
          </label>
        }
      >
        <select
          id={`${id}-granularity`}
          className="h-9 rounded-sm bg-input/50 px-3 text-sm"
          value={typeof granularity.input === "number" ? granularity.input : 15}
          onChange={(event) => granularity.onChange(Number(event.currentTarget.value))}
        >
          {[1, 5, 10, 15].map((minutes) => (
            <option key={minutes} value={minutes}>
              {minutes} minutes
            </option>
          ))}
        </select>
      </SettingRow>
      <NumericSetting form={form} name="maxBookingDurationMinutes" label="Maximum duration (minutes)" />
      <NumericSetting form={form} name="bufferBeforeMinutes" label="Buffer before (minutes)" />
      <NumericSetting form={form} name="bufferAfterMinutes" label="Buffer after (minutes)" />
      <SettingRow
        label={
          <label htmlFor={`${id}-double`} className={LABEL_CLASS_NAME}>
            Allow double booking
          </label>
        }
      >
        <Checkbox
          id={`${id}-double`}
          className="self-start"
          checked={doubleBooking.input === true}
          onCheckedChange={(checked) => doubleBooking.onChange(checked === true)}
        />
      </SettingRow>
    </>
  );
}
type Hours = { start: string; end: string };
type DayHours = Partial<Record<string, Hours>>;
const without = (hours: DayHours, day: string) =>
  Object.fromEntries(Object.entries(hours).filter(([key]) => key !== day));
const range = ({ start, end }: Hours) => `${start}–${end === "24:00" ? "00:00" : end}`;

// One 36px line, so a day switching between its read-out and its inputs keeps its row height.
function TimeRange({
  labelledBy,
  hours,
  onChange,
}: {
  labelledBy: string;
  hours: Hours;
  onChange: (hours: Hours) => void;
}) {
  const id = useId();
  const error = !validOpeningHours(hours.start, hours.end);
  const input = (boundary: keyof Hours) => (
    <Input
      aria-label={boundary === "start" ? "Opens" : "Closes"}
      type="time"
      step={60}
      required
      value={boundary === "end" && hours.end === "24:00" ? "00:00" : hours[boundary]}
      aria-invalid={error || undefined}
      aria-describedby={error ? `${id}-error` : undefined}
      onChange={(event) => {
        const value = event.currentTarget.value;
        onChange({ ...hours, [boundary]: boundary === "end" && value === "00:00" ? "24:00" : value });
      }}
    />
  );
  return (
    <fieldset aria-labelledby={labelledBy} className="min-w-0 space-y-2">
      <div className="grid max-w-xs grid-cols-[minmax(6.5rem,1fr)_auto_minmax(6.5rem,1fr)] items-center gap-2">
        {input("start")}
        <span aria-hidden="true" className="text-muted-foreground">
          –
        </span>
        {input("end")}
      </div>
      {error && <FieldError id={`${id}-error`}>Closing time must follow opening time.</FieldError>}
    </fieldset>
  );
}

function OpeningHours({
  form,
  days,
  dayHours,
  onDayHoursChange,
  drafts,
  onDraftsChange,
}: {
  form: FormStore;
  days: string[];
  dayHours: DayHours;
  onDayHoursChange: (dayHours: DayHours) => void;
  /** Hours being edited but not yet confirmed, by day. */
  drafts: DayHours;
  onDraftsChange: (drafts: DayHours) => void;
}) {
  const start = useField(form, { path: ["openingStart"] });
  const end = useField(form, { path: ["openingEnd"] });
  const id = useId();
  const shared = {
    start: typeof start.input === "string" ? start.input : "",
    end: typeof end.input === "string" ? end.input : "",
  };
  const customised = days.some((day) => dayHours[day]);
  const [byDay, setByDay] = useState(false);
  const iconButton = (label: string, icon: ReactNode, onClick: () => void, disabled = false) => (
    <Button
      type="button"
      variant="ghost"
      size="icon-sm"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
    >
      {icon}
    </Button>
  );
  return (
    <SettingRow
      label={
        <span id={`${id}-label`} className={LABEL_CLASS_NAME}>
          Opening hours
        </span>
      }
    >
      <TimeRange
        labelledBy={`${id}-label`}
        hours={shared}
        onChange={(next) => {
          start.onChange(next.start);
          end.onChange(next.end);
        }}
      />
      {byDay || customised ? (
        <>
          <ul aria-label="Hours by day" className="divide-y rounded-sm border">
            {days.map((day) => {
              const own = dayHours[day];
              const draft = drafts[day];
              const confirm = () => {
                if (!draft) return;
                // Confirming the shared hours leaves the day following them.
                onDayHoursChange(
                  draft.start === shared.start && draft.end === shared.end
                    ? without(dayHours, day)
                    : { ...dayHours, [day]: draft },
                );
                onDraftsChange(without(drafts, day));
              };
              return (
                // A day's inputs wrap below its name once the row is too narrow for both.
                <li key={day} className="flex flex-wrap items-start gap-x-3 gap-y-1 px-3 py-2">
                  <span id={`${id}-${day}`} className="flex min-h-9 w-24 shrink-0 items-center text-sm font-medium">
                    {day}
                  </span>
                  {draft ? (
                    <div className="grow">
                      <TimeRange
                        labelledBy={`${id}-${day}`}
                        hours={draft}
                        onChange={(next) => onDraftsChange({ ...drafts, [day]: next })}
                      />
                    </div>
                  ) : (
                    // px-3 lines the read-out digits up with the text inside an input.
                    <span
                      className={`flex min-h-9 grow items-center px-3 text-sm ${own ? "font-bold" : "text-muted-foreground"}`}
                    >
                      {range(own ?? shared)}
                    </span>
                  )}
                  {/* The first button stays mounted across states, so focus stays on it from edit to confirm. */}
                  <div className="ml-auto flex min-h-9 items-center gap-1">
                    {draft
                      ? iconButton(
                          `Confirm ${day} hours`,
                          <CheckIcon aria-hidden="true" />,
                          confirm,
                          !validOpeningHours(draft.start, draft.end),
                        )
                      : iconButton(`Edit ${day} hours`, <PencilIcon aria-hidden="true" />, () =>
                          onDraftsChange({ ...drafts, [day]: own ?? shared }),
                        )}
                    {draft
                      ? iconButton(`Discard ${day} changes`, <XIcon aria-hidden="true" />, () =>
                          onDraftsChange(without(drafts, day)),
                        )
                      : own &&
                        iconButton(`Use shared hours on ${day}`, <Undo2Icon aria-hidden="true" />, () =>
                          onDayHoursChange(without(dayHours, day)),
                        )}
                  </div>
                </li>
              );
            })}
          </ul>
          {/* Resets every day to the shared hours; Cancel still restores the saved configuration. */}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="self-start"
            onClick={() => {
              onDraftsChange({});
              onDayHoursChange({});
              setByDay(false);
            }}
          >
            Use the same hours every day
          </Button>
        </>
      ) : (
        <Button type="button" variant="ghost" size="sm" className="self-start" onClick={() => setByDay(true)}>
          Set different hours for some days
        </Button>
      )}
    </SettingRow>
  );
}

function ConfigurationPrototype({ initialDayHours = {} }: { initialDayHours?: DayHours }) {
  const id = useId();
  const [saved, setSaved] = useState(INITIAL);
  const [savedDays, setSavedDays] = useState(DAYS);
  const [days, setDays] = useState(DAYS);
  const [savedDayHours, setSavedDayHours] = useState(initialDayHours);
  const [dayHours, setDayHours] = useState(initialDayHours);
  const [drafts, setDrafts] = useState<DayHours>({});
  const [editing, setEditing] = useState(true);
  const [notice, setNotice] = useState("");
  const form = useForm({ schema: BookingConfigurationUpdateInputSchema, initialInput: saved });
  const empty = days.length === 0;
  // An unconfirmed day edit must be confirmed or discarded before saving.
  const invalid = empty || days.some((day) => drafts[day]);
  const savedExceptions = savedDays.filter((day) => savedDayHours[day]);
  const cancel = () => {
    reset(form, { initialInput: saved });
    setDays(savedDays);
    setDayHours(savedDayHours);
    setDrafts({});
    setNotice("");
    setEditing(false);
  };

  return (
    <main className="min-h-screen bg-background p-4 text-foreground sm:p-8">
      <div className="mx-auto max-w-5xl space-y-6">
        <header className="space-y-2">
          <Heading level={2} as="h1">
            Confocal microscope
          </Heading>
        </header>
        <Card>
          <CardHeader>
            <CardTitle>Booking rules</CardTitle>
            <CardAction className="flex gap-2">
              {editing ? (
                <>
                  <Button key="save" type="submit" size="sm" form={id} disabled={invalid}>
                    Save changes
                  </Button>
                  <Button key="cancel" type="button" size="sm" variant="ghost" onClick={cancel}>
                    Cancel
                  </Button>
                </>
              ) : (
                <Button
                  key="edit"
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    reset(form, { initialInput: saved });
                    setDays(savedDays);
                    setDayHours(savedDayHours);
                    setDrafts({});
                    setNotice("");
                    setEditing(true);
                  }}
                >
                  Edit configuration
                </Button>
              )}
            </CardAction>
          </CardHeader>
          <CardContent>
            {editing ? (
              <Form
                id={id}
                of={form}
                className="space-y-4"
                onSubmit={(input) => {
                  if (invalid) return;
                  setSaved(input);
                  setSavedDays([...days]);
                  // Hours kept for a deselected day are dropped on save.
                  const kept = Object.fromEntries(Object.entries(dayHours).filter(([day]) => days.includes(day)));
                  setSavedDayHours(kept);
                  setDayHours(kept);
                  setDrafts({});
                  setEditing(false);
                  setNotice("Booking configuration saved.");
                }}
              >
                <RenderFields fields={fields} form={form} layout="inline" />
                <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
                  <div className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
                    <SettingRow
                      label={
                        <span id={`${id}-days-label`} className={LABEL_CLASS_NAME}>
                          Open on
                        </span>
                      }
                    >
                      <fieldset
                        aria-labelledby={`${id}-days-label`}
                        aria-describedby={empty ? `${id}-days-error` : undefined}
                        className="min-w-0 space-y-2"
                      >
                        <div className="relative flex min-h-9 flex-nowrap items-center gap-4 overflow-x-auto">
                          {DAYS.map((day) => (
                            <div key={day} className="flex shrink-0 items-center gap-2">
                              <Checkbox
                                id={`${id}-${day}`}
                                aria-labelledby={`${id}-${day}-label`}
                                checked={days.includes(day)}
                                aria-invalid={empty || undefined}
                                onCheckedChange={(checked) => {
                                  setDays((current) =>
                                    DAYS.filter((candidate) =>
                                      candidate === day ? checked === true : current.includes(candidate),
                                    ),
                                  );
                                  setNotice("");
                                }}
                              />
                              <label
                                id={`${id}-${day}-label`}
                                htmlFor={`${id}-${day}`}
                                className="cursor-pointer text-sm"
                                title={day}
                              >
                                <span aria-hidden="true">{day.slice(0, 3)}</span>
                                <span className="sr-only">{day}</span>
                              </label>
                            </div>
                          ))}
                        </div>
                        {empty && <FieldError id={`${id}-days-error`}>Select at least one day.</FieldError>}
                      </fieldset>
                    </SettingRow>
                    <OpeningHours
                      form={form}
                      days={days}
                      dayHours={dayHours}
                      onDayHoursChange={setDayHours}
                      drafts={drafts}
                      onDraftsChange={setDrafts}
                    />
                    <OtherSettings form={form} />
                  </div>
                </div>
              </Form>
            ) : (
              <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
                <dl className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-3 text-sm`}>
                  <dt className="text-muted-foreground">Open on</dt>
                  <dd>{savedDays.length === 7 ? "Every day" : savedDays.join(", ")}</dd>
                  <dt className="text-muted-foreground">Opening hours</dt>
                  <dd>
                    {savedExceptions.length === 0 ? (
                      range({ start: saved.openingStart, end: saved.openingEnd })
                    ) : (
                      <ul className="space-y-1">
                        {savedDays.map((day) => {
                          const own = savedDayHours[day];
                          return (
                            <li key={day}>
                              {day}:{" "}
                              {own ? (
                                <strong>{range(own)}</strong>
                              ) : (
                                range({ start: saved.openingStart, end: saved.openingEnd })
                              )}
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </dd>
                  <dt className="text-muted-foreground">Time zone</dt>
                  <dd>Europe/Berlin</dd>
                </dl>
              </div>
            )}
          </CardContent>
        </Card>
        {notice && (
          <p role="status" className="text-sm">
            {notice}
          </p>
        )}
      </div>
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
    initialDayHours: {
      Friday: { start: "09:00", end: "14:00" },
      Saturday: { start: "10:00", end: "16:00" },
    },
  },
};
