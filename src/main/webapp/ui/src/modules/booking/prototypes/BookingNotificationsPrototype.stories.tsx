// PROTOTYPE ONLY: in-memory exploration of personal booking notification choices.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy stays out of production catalogs. */
import { Form, isDirty, useForm } from "@formisch/react";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { BellIcon, BellOffIcon, CalendarDaysIcon, MicroscopeIcon, SettingsIcon } from "lucide-react";
import { useState } from "react";
import * as v from "valibot";
import type { BookingDisplayPreferencesInput } from "@/modules/booking/domain/bookingDisplayPreferences";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { BookableItemFactsAside } from "../pages/bookable-items/BookableItemFactsAside";
import { BookableItemSpotlightHeader } from "../pages/bookable-items/BookableItemSpotlightHeader";
import { BookingConfigurationSchema } from "../pages/bookable-items/bookingConfiguration";
import { bookableItemFixtures, bookerBookingAccess } from "../pages/bookable-items/mocks/bookableItemsMocks";
import { detailPageClassName } from "../pages/DetailPageShell";
import { BookingDisplaySettingsFields } from "../pages/preferences/BookingDisplaySettingsFields";
import {
  BookingNotificationChoiceFields,
  BookingNotificationChoiceSchema,
  bookingNotificationChoice,
} from "../pages/preferences/BookingNotificationChoiceFields";

type View = "preferences" | "item" | "profile";
type ItemRole = "owner" | "viewer" | "booker";

const roleItems = {
  owner: { fixture: bookableItemFixtures[0] },
  viewer: { fixture: bookableItemFixtures[3] },
  booker: { fixture: bookableItemFixtures[4] },
} as const;

const viewerBookingAccess = {
  effectiveRole: "VIEWER",
  roleSources: [],
  capabilities: {
    canEditConfiguration: false,
    canViewAudit: false,
    canViewAccess: false,
    canManageAssignments: false,
    canManageOwners: false,
    canCreateBooking: false,
    canManageOwnBookings: false,
    canManageAllEvents: false,
    canCreateBlockout: false,
    canSubscribeCalendar: false,
    canLeaveConfiguration: false,
    canManageNotificationSubscription: true,
  },
} as const;

function ChoiceEditor({
  value,
  label,
  description,
  onSave,
}: {
  value: boolean;
  label: string;
  description: string;
  onSave: (value: boolean) => void;
}) {
  const form = useForm({
    schema: BookingNotificationChoiceSchema,
    initialInput: bookingNotificationChoice(value),
  });
  const dirty = isDirty(form);

  return (
    <Form of={form} className="space-y-3 rounded-sm border p-4" onSubmit={(input) => onSave(input.choice === "ON")}>
      <BookingNotificationChoiceFields form={form} labelKey={label} descriptionKey={description} />
      <Button type="submit" size="sm" disabled={!dirty}>
        Save
      </Button>
    </Form>
  );
}

function BookingNotificationsPrototype({
  initialView = "preferences",
  initialRole = "owner",
  initiallySubscribed = true,
  globallyCreated = true,
  globallyCancelled = true,
  emailEnabled = false,
}: {
  initialView?: View;
  initialRole?: ItemRole;
  initiallySubscribed?: boolean;
  globallyCreated?: boolean;
  globallyCancelled?: boolean;
  emailEnabled?: boolean;
}) {
  const [display, setDisplay] = useState<BookingDisplayPreferencesInput>({
    availabilityWindowStart: "08:00",
    availabilityWindowEnd: "18:00",
    timezoneMode: "BROWSER",
    customTimezone: null,
  });
  const [view, setView] = useState<View>(initialView);
  const [role, setRole] = useState<ItemRole>(initialRole);
  const [autoSubscribeOwnedItems, setAutoSubscribeOwnedItems] = useState(true);
  const [created, setCreated] = useState(globallyCreated);
  const [cancelled, setCancelled] = useState(globallyCancelled);
  const [email, setEmail] = useState(emailEnabled);
  const [subscriptions, setSubscriptions] = useState<Record<string, boolean>>({
    IN123: initialRole === "owner" ? initiallySubscribed : true,
    IN126: initialRole === "viewer" ? initiallySubscribed : false,
    IN127: initialRole === "booker" ? initiallySubscribed : false,
  });
  const [saved, setSaved] = useState("");
  const selected = roleItems[role];
  const configuration = v.parse(
    BookingConfigurationSchema,
    role === "owner"
      ? selected.fixture
      : {
          ...selected.fixture,
          ...(role === "viewer" ? viewerBookingAccess : bookerBookingAccess),
          effectiveRole: role.toUpperCase(),
          capabilities: {
            ...(role === "viewer" ? viewerBookingAccess.capabilities : bookerBookingAccess.capabilities),
            canManageNotificationSubscription: true,
          },
        },
  );
  const target = configuration.target;
  if (!target) throw new Error("Prototype fixture needs a booking instrument.");

  const subscribed = subscriptions[target.globalId] === true;
  const delivery = (eventEnabled: boolean) =>
    !subscribed
      ? "Off for this item"
      : !eventEnabled
        ? "Off in My Profile"
        : email
          ? "RSpace and email"
          : "RSpace only";

  function navigate(next: View) {
    setView(next);
    setSaved("");
  }

  function saveChoice(label: string, action: () => void) {
    action();
    setSaved(label + " saved for you.");
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div className="flex items-center gap-3">
          <CalendarDaysIcon className="size-6 text-primary" />
          <span className="text-lg font-semibold">RSpace · Booking</span>
          <Badge variant="outline">Prototype · Codex</Badge>
        </div>
        <span className="text-sm text-muted-foreground">Alex Morgan</span>
      </header>
      <div className="mx-auto max-w-7xl">
        <nav aria-label="Prototype page switcher" className="flex flex-wrap gap-2 border-b p-4">
          {(
            [
              { view: "preferences", label: "Booking preferences", icon: SettingsIcon },
              { view: "item", label: "Bookable item", icon: MicroscopeIcon },
              { view: "profile", label: "My Profile", icon: BellIcon },
            ] as const
          ).map(({ view: destination, label, icon: Icon }) => (
            <Button
              key={destination}
              type="button"
              variant={view === destination ? "secondary" : "ghost"}
              className="justify-start"
              aria-current={view === destination ? "page" : undefined}
              onClick={() => navigate(destination)}
            >
              <Icon className="size-4" />
              {label}
            </Button>
          ))}
          {view === "item" ? (
            <label className="ml-auto flex items-center gap-2 text-sm text-muted-foreground">
              Item
              <select
                aria-label="Item"
                value={role}
                onChange={(event) => {
                  setRole(event.currentTarget.value as ItemRole);
                  setSaved("");
                }}
                className="rounded-sm border bg-background px-2 py-1 text-foreground"
              >
                <option value="owner">Confocal microscope</option>
                <option value="viewer">Flow cytometer</option>
                <option value="booker">Microplate reader</option>
              </select>
            </label>
          ) : null}
        </nav>

        {view === "preferences" ? (
          <main className="space-y-6 p-4 sm:p-8">
            <div>
              <p className="mb-2 text-sm text-muted-foreground">Booking / Your preferences</p>
              <h1 className="text-2xl font-semibold">Booking preferences</h1>
            </div>
            <details className="max-w-2xl rounded-sm border p-4">
              <summary className="cursor-pointer font-medium">Display preferences</summary>
              <div className="mt-5">
                <BookingDisplaySettingsFields
                  value={display}
                  onChange={setDisplay}
                  browserTimezone="Europe/Berlin"
                  institutionTimezone="Europe/Berlin"
                />
              </div>
            </details>
            <section className="max-w-2xl space-y-4" aria-labelledby="booking-notifications-heading">
              <div>
                <h2 id="booking-notifications-heading" className="text-lg font-semibold">
                  Booking notifications
                </h2>
              </div>
              <ChoiceEditor
                key={autoSubscribeOwnedItems ? "auto-on" : "auto-off"}
                value={autoSubscribeOwnedItems}
                label="Automatically subscribe to new bookable items I own"
                description="Existing subscriptions stay unchanged."
                onSave={(value) => saveChoice("Future owner subscriptions", () => setAutoSubscribeOwnedItems(value))}
              />
              <div className="space-y-3 rounded-sm border p-4">
                <p className="text-sm text-muted-foreground">
                  Keeps your automatic-subscribe default and My Profile settings.
                </p>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setSubscriptions(Object.fromEntries(Object.keys(subscriptions).map((id) => [id, false])));
                    setSaved("Unsubscribed from all bookable items. Your future owner default is unchanged.");
                  }}
                >
                  Unsubscribe from all items
                </Button>
              </div>
              <p className="text-sm text-muted-foreground">Manage event and email preferences in My Profile.</p>
            </section>
          </main>
        ) : null}

        {view === "item" ? (
          <main className={detailPageClassName}>
            <BookableItemSpotlightHeader configuration={configuration} target={target} />
            <div className="flex flex-wrap gap-5 border-b pb-3 text-sm">
              <span className="font-semibold text-primary">Bookings</span>
              <span>Details</span>
              <span>Activity</span>
              <span>Access</span>
            </div>
            <section className="max-w-2xl space-y-4 rounded-sm border p-5" aria-labelledby="item-notifications-heading">
              <div>
                <h2 id="item-notifications-heading" className="flex items-center gap-2 text-lg font-semibold">
                  <BellIcon aria-hidden="true" className="size-5" />
                  Your notifications
                </h2>
              </div>
              <ChoiceEditor
                key={target.globalId + (subscribed ? "-on" : "-off")}
                value={subscribed}
                label="Receive booking notifications"
                description="When someone else creates or cancels a booking."
                onSave={(value) =>
                  saveChoice("Item notification choice", () =>
                    setSubscriptions((current) => ({ ...current, [target.globalId]: value })),
                  )
                }
              />
              <div className="rounded-sm bg-muted p-4">
                <p className="flex items-center gap-2 font-medium">
                  {subscribed && (created || cancelled) ? (
                    <BellIcon aria-hidden="true" className="size-4" />
                  ) : (
                    <BellOffIcon aria-hidden="true" className="size-4" />
                  )}
                  What reaches you
                </p>
                <dl className="mt-3 space-y-2 text-sm">
                  <div className="flex justify-between gap-4">
                    <dt>New bookings</dt>
                    <dd className="text-right text-muted-foreground">{delivery(created)}</dd>
                  </div>
                  <div className="flex justify-between gap-4">
                    <dt>Cancellations</dt>
                    <dd className="text-right text-muted-foreground">{delivery(cancelled)}</dd>
                  </div>
                </dl>
                {subscribed && (!created || !cancelled) ? (
                  <Button type="button" variant="link" className="h-auto px-0 pt-3" onClick={() => navigate("profile")}>
                    Manage My Profile preferences
                  </Button>
                ) : null}
              </div>
            </section>
            <BookableItemFactsAside configuration={configuration} displayTimeZone="Europe/Berlin" />
          </main>
        ) : null}

        {view === "profile" ? (
          <main className="mx-auto max-w-3xl space-y-6 p-4 sm:p-8">
            <div>
              <p className="mb-2 text-sm text-muted-foreground">My Profile / Notification settings</p>
              <h1 className="text-2xl font-semibold">Booking notifications</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                Applies to all subscribed items. Your subscriptions stay unchanged.
              </p>
            </div>
            <section className="space-y-3" aria-label="Booking notification event and email choices">
              {(
                [
                  {
                    key: "created",
                    value: created,
                    label: "New bookings",
                    description: "When someone else books an item.",
                    setValue: setCreated,
                  },
                  {
                    key: "cancelled",
                    value: cancelled,
                    label: "Booking cancellations",
                    description: "When someone else cancels a booking.",
                    setValue: setCancelled,
                  },
                  {
                    key: "email",
                    value: email,
                    label: "Also send RSpace notifications by email",
                    description: "RSpace notifications stay on when email is off.",
                    setValue: setEmail,
                  },
                ] as const
              ).map(({ key, value, label, description, setValue }) => (
                <ChoiceEditor
                  key={key + (value ? "-on" : "-off")}
                  value={value}
                  label={label}
                  description={description}
                  onSave={(next) => saveChoice(label, () => setValue(next))}
                />
              ))}
            </section>
          </main>
        ) : null}

        <p role="status" className="mx-auto max-w-7xl px-4 pb-5 text-sm text-primary sm:px-8">
          {saved}
        </p>
      </div>
    </div>
  );
}

const meta = {
  title: "Booking/Prototypes/Notifications (Codex)",
  component: BookingNotificationsPrototype,
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof BookingNotificationsPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;
export const Preferences: Story = {};
export const OwnerItemSubscribed: Story = { args: { initialView: "item", initialRole: "owner" } };
export const OwnerItemUnsubscribed: Story = {
  args: { initialView: "item", initialRole: "owner", initiallySubscribed: false },
};
export const ViewerCanOptIn: Story = {
  args: { initialView: "item", initialRole: "viewer", initiallySubscribed: false },
};
export const BookerCanOptIn: Story = {
  args: { initialView: "item", initialRole: "booker", initiallySubscribed: false },
};
export const NewBookingsOff: Story = { args: { initialView: "item", globallyCreated: false } };
export const CancellationsOff: Story = { args: { initialView: "item", globallyCancelled: false } };
export const ProfileEventAndEmailChoices: Story = { args: { initialView: "profile" } };
export const ProfileEmailOn: Story = { args: { initialView: "profile", emailEnabled: true } };
