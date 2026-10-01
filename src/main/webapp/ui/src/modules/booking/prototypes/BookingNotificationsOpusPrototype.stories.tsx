// PROTOTYPE ONLY. Throwaway Opus 5.5 concept for Plan 008 booking notification subscriptions; not production code.
// Prototype question: can every reader of an instrument (owner, booker, viewer) manage only their own
// subscription from the production item page, starting OFF unless saved, while My Profile event and email
// preferences stay separate gates rather than reading as "unsubscribed"?
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Tabs } from "@base-ui/react/tabs";
import { Form, isDirty, reset, useForm } from "@formisch/react";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { BellIcon, CheckIcon } from "lucide-react";
import { useState } from "react";
import type { BookingDisplayPreferencesInput } from "@/modules/booking/domain/bookingDisplayPreferences";
import { BookableItemFactsAside } from "@/modules/booking/pages/bookable-items/BookableItemFactsAside";
import { BookableItemRulesReadOut } from "@/modules/booking/pages/bookable-items/BookableItemRulesReadOut";
import { BookableItemSpotlightHeader } from "@/modules/booking/pages/bookable-items/BookableItemSpotlightHeader";
import type { BookingConfiguration } from "@/modules/booking/pages/bookable-items/bookingConfiguration";
import {
  bookableItemFixtures,
  bookerBookingAccess,
  sampleBookingEvents,
} from "@/modules/booking/pages/bookable-items/mocks/bookableItemsMocks";
import { detailColumnsClassName, detailPageClassName } from "@/modules/booking/pages/DetailPageShell";
import { BookingDisplaySettingsFields } from "@/modules/booking/pages/preferences/BookingDisplaySettingsFields";
import {
  BookingNotificationChoiceFields,
  BookingNotificationChoiceSchema,
  bookingNotificationChoice,
} from "@/modules/booking/pages/preferences/BookingNotificationChoiceFields";
import { inheritedBrowserBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Label } from "@/modules/common/ui/label";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";

type View = "preferences" | "item" | "profile";
type Role = "owner" | "booker" | "viewer";
type Events = { created: boolean; cancelled: boolean; email: boolean };

// ponytail: fixture configs re-typed; `as const` roleSources is readonly and must be replaced.
const owned = (index: 0 | 1): BookingConfiguration => ({
  ...bookableItemFixtures[index],
  roleSources: [],
  capabilities: { ...bookableItemFixtures[index].capabilities, canManageNotificationSubscription: true },
});
// Plan 008: every current reader gets the personal subscription capability, not only owners.
const booked = (index: 4): BookingConfiguration => ({
  ...bookableItemFixtures[index],
  ...bookerBookingAccess,
  roleSources: [],
  capabilities: { ...bookerBookingAccess.capabilities, canManageNotificationSubscription: true },
});
const viewed = (index: 3): BookingConfiguration => ({
  ...bookableItemFixtures[index],
  ...bookerBookingAccess,
  effectiveRole: "VIEWER",
  roleSources: [],
  capabilities: {
    ...bookerBookingAccess.capabilities,
    canCreateBooking: false,
    canManageOwnBookings: false,
    canSubscribeCalendar: false,
    canManageNotificationSubscription: true,
  },
});
const items: Record<string, { config: BookingConfiguration; role: Role }> = {
  IN123: { config: owned(0), role: "owner" },
  IN124: { config: owned(1), role: "owner" },
  IN126: { config: viewed(3), role: "viewer" },
  IN127: { config: booked(4), role: "booker" },
};
// Saved rows for the signed-in user only. Absent = no saved choice, which reads as OFF.
// Non-owners start absent: sharing never opts a reader in.
const initialSubscriptions: Record<string, boolean> = { IN123: true, IN124: false };
const pageTabClassName =
  "-mb-px cursor-default border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted-foreground transition-colors outline-none select-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/30 aria-selected:border-primary aria-selected:text-foreground";

function Saved({ visible, children }: { visible: boolean; children: string }) {
  return (
    <span role="status" className="inline-flex min-h-5 items-center gap-1 text-xs text-muted-foreground">
      {visible ? (
        <>
          <CheckIcon aria-hidden="true" className="size-3.5 text-emerald-600" />
          {children}
        </>
      ) : null}
    </span>
  );
}

// ponytail: saves apply instantly in memory; production pending, 409 and error states are unchanged and omitted.
function ItemNotificationCard({
  saved,
  events,
  onSave,
  onOpenProfile,
}: {
  saved: boolean | undefined;
  events: Events;
  onSave: (enabled: boolean) => void;
  onOpenProfile: () => void;
}) {
  const enabled = saved === true;
  const form = useForm({ schema: BookingNotificationChoiceSchema, initialInput: bookingNotificationChoice(enabled) });
  const [justSaved, setJustSaved] = useState(false);
  const dirty = isDirty(form);
  const eventRows = [
    ["New bookings", events.created],
    ["Booking cancellations", events.cancelled],
  ] as const;

  return (
    <Card size="sm" aria-labelledby="item-notification-subscription-heading">
      <CardHeader>
        <CardTitle id="item-notification-subscription-heading" className="flex items-center gap-2">
          <BellIcon aria-hidden="true" className="size-4" />
          Instrument notifications
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <Form
          of={form}
          className="space-y-3"
          onSubmit={(input) => {
            onSave(input.choice === "ON");
            reset(form, { initialInput: input });
            setJustSaved(true);
          }}
        >
          <BookingNotificationChoiceFields
            form={form}
            labelKey="Receive booking notifications"
            descriptionKey="When someone else creates or cancels a booking."
          />
          <div className="flex items-center gap-3">
            <Button type="submit" size="sm" disabled={!dirty}>
              Save
            </Button>
            <Saved visible={justSaved && !dirty}>Notification subscription saved.</Saved>
          </div>
        </Form>
        {!enabled ? (
          <p className="rounded-sm bg-muted p-3 text-sm text-muted-foreground">
            Notifications are off for this instrument.
          </p>
        ) : (
          <div className="space-y-3">
            <dl className="space-y-2 text-sm">
              {eventRows.map(([label, on]) => (
                <div key={label} className="flex justify-between gap-3">
                  <dt>{label}</dt>
                  <dd className="text-right text-muted-foreground">
                    {on ? (events.email ? "RSpace and email" : "RSpace only") : "Paused by My Profile"}
                  </dd>
                </div>
              ))}
            </dl>
            {!events.created || !events.cancelled || !events.email ? (
              <div className="space-y-2 rounded-sm border p-3 text-sm">
                <p>
                  {!events.created && !events.cancelled
                    ? "Both booking events are off in My Profile."
                    : !events.created || !events.cancelled
                      ? "One booking event is off in My Profile."
                      : "Email is off. Notifications arrive in RSpace only."}
                </p>
                <Button type="button" variant="link" className="h-auto p-0" onClick={onOpenProfile}>
                  Manage My Profile preferences
                </Button>
              </div>
            ) : null}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function AutoSubscribeForm({ value, onSave }: { value: boolean; onSave: (value: boolean) => void }) {
  const form = useForm({ schema: BookingNotificationChoiceSchema, initialInput: bookingNotificationChoice(value) });
  const [justSaved, setJustSaved] = useState(false);
  const dirty = isDirty(form);
  return (
    <Form
      of={form}
      className="space-y-3 rounded-sm border p-4"
      onSubmit={(input) => {
        onSave(input.choice === "ON");
        reset(form, { initialInput: input });
        setJustSaved(true);
      }}
    >
      <BookingNotificationChoiceFields
        form={form}
        labelKey="notificationSubscriptions.preferences.autoSubscribe.label"
        descriptionKey="Existing subscriptions stay unchanged."
      />
      <div className="flex items-center gap-3">
        <Button type="submit" size="sm" disabled={!dirty}>
          Save
        </Button>
        <Saved visible={justSaved && !dirty}>Notification preference saved.</Saved>
      </div>
    </Form>
  );
}

type PrototypeProps = {
  view: View;
  itemId: keyof typeof items;
  autoSubscribe: boolean;
  created: boolean;
  cancelled: boolean;
  email: boolean;
};

function NotificationsPrototype(props: PrototypeProps) {
  const [view, setView] = useState(props.view);
  const [itemId, setItemId] = useState(props.itemId);
  const [tab, setTab] = useState("bookings");
  const [autoSubscribe, setAutoSubscribe] = useState(props.autoSubscribe);
  const [events, setEvents] = useState<Events>({
    created: props.created,
    cancelled: props.cancelled,
    email: props.email,
  });
  const [subscriptions, setSubscriptions] = useState(initialSubscriptions);
  const [unsubscribedCount, setUnsubscribedCount] = useState<number>();
  const { institutionTimezone, overridden: _overridden, ...initialDisplay } = inheritedBrowserBookingPreferences;
  const [display, setDisplay] = useState<BookingDisplayPreferencesInput>(initialDisplay);
  const browserTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  const openItem = (id: string) => {
    setItemId(id);
    setTab("bookings");
    setView("item");
  };

  const preferencesView = (
    <main className="space-y-6 p-4 sm:p-8">
      <div>
        <Heading level={3} as="h1" className="mb-2">
          Booking preferences
        </Heading>
        <p className="text-sm text-muted-foreground">
          Choose how Booking dates, times, forms, and availability are displayed.
        </p>
      </div>
      <Separator />
      <div className="max-w-2xl">
        {/* ponytail: production display fields on local state; their Save/Reset flow is unchanged and omitted. */}
        <BookingDisplaySettingsFields
          value={display}
          onChange={setDisplay}
          browserTimezone={browserTimezone}
          institutionTimezone={institutionTimezone}
        />
      </div>
      <Separator />
      <section className="max-w-2xl space-y-4" aria-labelledby="booking-notifications-heading">
        <div>
          <Heading level={4} as="h2" id="booking-notifications-heading">
            Booking notifications
          </Heading>
        </div>
        <AutoSubscribeForm value={autoSubscribe} onSave={setAutoSubscribe} />
        <div className="space-y-3 rounded-sm border p-4">
          <p className="text-sm text-muted-foreground">
            Keeps your automatic-subscribe default and My Profile settings.
          </p>
          {unsubscribedCount !== undefined ? (
            <p role="status" className="text-sm text-muted-foreground">
              Unsubscribed from {unsubscribedCount} {unsubscribedCount === 1 ? "instrument" : "instruments"}. Default
              for new owned instruments is still {autoSubscribe ? "On" : "Off"}.
            </p>
          ) : null}
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setUnsubscribedCount(Object.values(subscriptions).filter(Boolean).length);
              setSubscriptions(Object.fromEntries(Object.keys(subscriptions).map((id) => [id, false])));
            }}
          >
            Unsubscribe from all instruments
          </Button>
          <p className="text-sm text-muted-foreground">
            Manage multiple subscriptions in{" "}
            <a
              className="underline"
              href="/?path=/story/booking-prototypes-notifications-bulk-opus-5-5--mixed-ownership-selection"
              target="_top"
            >
              All bookable items
            </a>
            .
          </p>
        </div>
      </section>
    </main>
  );

  const { config } = items[itemId];
  const target = config.target;
  const itemView =
    target === null ? null : (
      <main className={detailPageClassName}>
        <div className="@container">
          <Tabs.Root value={tab} onValueChange={(value) => setTab(String(value))} className="min-w-0 space-y-6">
            <BookableItemSpotlightHeader configuration={config} target={target} />
            <Tabs.List className="flex flex-wrap border-b">
              {["bookings", "details", "access"].map((value) => (
                <Tabs.Tab key={value} value={value} className={pageTabClassName}>
                  {value[0].toUpperCase() + value.slice(1)}
                </Tabs.Tab>
              ))}
            </Tabs.List>
            <div className={detailColumnsClassName}>
              <div className="min-w-0">
                <Tabs.Panel value="bookings" className="space-y-4 outline-none">
                  <Heading level={3} as="h2">
                    Upcoming
                  </Heading>
                  <ul className="divide-y rounded-sm border text-sm">
                    {sampleBookingEvents
                      .filter((booking) => booking.target.globalId === target.globalId)
                      .map((booking) => (
                        <li key={booking.id} className="flex flex-wrap justify-between gap-2 p-3">
                          <span>{booking.purpose ?? "Busy"}</span>
                          <span className="text-muted-foreground">{booking.bookedBy}</span>
                        </li>
                      ))}
                    <li className="p-3 text-muted-foreground">Sample rows; production list not mounted.</li>
                  </ul>
                </Tabs.Panel>
                <Tabs.Panel value="details" className="outline-none">
                  <Card>
                    <CardHeader>
                      <CardTitle>Booking rules</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <BookableItemRulesReadOut configuration={config} />
                    </CardContent>
                  </Card>
                </Tabs.Panel>
                <Tabs.Panel value="access" className="text-sm text-muted-foreground outline-none">
                  Access assignments are unchanged by notifications. Subscribers are never listed, and nobody can change
                  another person's choice.
                </Tabs.Panel>
              </div>
              <div className="min-w-0 space-y-6">
                <ItemNotificationCard
                  key={itemId}
                  saved={subscriptions[itemId]}
                  events={events}
                  onSave={(enabled) => setSubscriptions((current) => ({ ...current, [itemId]: enabled }))}
                  onOpenProfile={() => setView("profile")}
                />
                <BookableItemFactsAside configuration={config} displayTimeZone={browserTimezone} />
              </div>
            </div>
          </Tabs.Root>
        </div>
      </main>
    );

  const profileView = (
    <main className="mx-auto max-w-2xl space-y-6 p-4 sm:p-8">
      <Heading level={3} as="h1">
        My Profile
      </Heading>
      <Card>
        <CardHeader>
          <CardTitle>Messaging preferences</CardTitle>
          <CardDescription>Applies to all subscribed instruments. Your subscriptions stay unchanged.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {(
            [
              ["created", "New bookings"],
              ["cancelled", "Booking cancellations"],
              ["email", "Also send notifications by email"],
            ] as const
          ).map(([key, label]) => (
            <Label key={key} className="gap-3 font-normal">
              <Checkbox
                aria-label={label}
                checked={events[key]}
                onCheckedChange={(checked) => setEvents((current) => ({ ...current, [key]: checked }))}
              />
              {label}
            </Label>
          ))}
          <p className="text-xs text-muted-foreground">RSpace notifications stay on when email is off.</p>
        </CardContent>
      </Card>
    </main>
  );

  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <div className="min-h-screen bg-background text-foreground">
        <nav aria-label="Prototype views" className="flex flex-wrap items-center gap-2 border-b px-4 py-2 sm:px-8">
          <Badge variant="outline">Prototype</Badge>
          {(
            [
              ["preferences", "Booking preferences"],
              ["item", "Bookable item"],
              ["profile", "My Profile"],
            ] as const
          ).map(([value, label]) => (
            <Button
              key={value}
              type="button"
              size="sm"
              variant={view === value ? "secondary" : "ghost"}
              aria-current={view === value ? "page" : undefined}
              onClick={() => setView(value)}
            >
              {label}
            </Button>
          ))}
          {view === "item" ? (
            <label className="ml-auto flex items-center gap-2 text-xs text-muted-foreground">
              Item
              <select
                value={itemId}
                onChange={(event) => openItem(event.currentTarget.value)}
                className="rounded-sm border bg-background px-2 py-1 text-xs"
              >
                {Object.entries(items).map(([id, item]) => (
                  <option key={id} value={id}>
                    {item.config.target?.value.name}
                  </option>
                ))}
              </select>
            </label>
          ) : null}
        </nav>
        {view === "preferences" ? preferencesView : null}
        {view === "item" ? itemView : null}
        {view === "profile" ? profileView : null}
      </div>
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Notifications (Opus 5.5)",
  component: NotificationsPrototype,
  parameters: { layout: "fullscreen" },
  args: {
    view: "preferences",
    itemId: "IN123",
    autoSubscribe: true,
    created: true,
    cancelled: true,
    email: true,
  },
} satisfies Meta<typeof NotificationsPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const PreferencesDefaultOn: Story = {};
export const PreferencesDefaultOff: Story = { args: { autoSubscribe: false } };
export const OwnerItemSubscribed: Story = { args: { view: "item" } };
export const OwnerItemOptedOut: Story = { args: { view: "item", itemId: "IN124" } };
export const ViewerItemNotSubscribed: Story = { args: { view: "item", itemId: "IN126" } };
export const BookerItemNotSubscribed: Story = { args: { view: "item", itemId: "IN127" } };
export const GlobalEventsOffPersonalOn: Story = { args: { view: "item", created: false, cancelled: false } };
export const EmailOffInProfile: Story = { args: { view: "item", email: false } };
export const ProfileMessagingPreferences: Story = { args: { view: "profile" } };
