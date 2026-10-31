// PROTOTYPE ONLY. Explore the recurring booking workflow (plan 004) on top of the production booking form,
// day timeline, item card and lists. Network reads are served by story-local MSW handlers; nothing is written.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */

import { Temporal } from "@js-temporal/polyfill";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { HttpResponse, http } from "msw";
import { setupWorker } from "msw/browser";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { CreateRecurringPrototype } from "./RecurringBookingFableCreate.story";
import { SeriesInListsPrototype } from "./RecurringBookingFableLists.story";
import { CONFLICTING_RULE, DEFAULT_RULE, listEnvelope, storedBookings } from "./RecurringBookingFableModel";
import { ScopePrototype } from "./RecurringBookingFableSeries.story";

const refused = () =>
  HttpResponse.json({ status: 501, detail: "Prototype: bookings are never written." }, { status: 501 });

const handlers = [
  oauthTokenHandler(true),
  http.get("/api/v2/bookings", ({ request }) => {
    const where = new URL(request.url).searchParams.get("where") ?? "";
    const before = /start=lt=([^;]+)/.exec(where)?.[1];
    const after = /end=gt=([^;]+)/.exec(where)?.[1];
    const docs = storedBookings.filter(
      (booking) =>
        (!before || Temporal.Instant.compare(booking.start, before) < 0) &&
        (!after || Temporal.Instant.compare(booking.end, after) > 0),
    );
    return HttpResponse.json(listEnvelope(docs));
  }),
  http.post("/api/v2/bookings", refused),
  http.patch("/api/v2/bookings/:id", refused),
  http.put("/api/v2/bookings/:id", refused),
  http.delete("/api/v2/bookings/:id", refused),
];

const prototypeWorker = setupWorker(...handlers);

// ---------------------------------------------------------------------------
// Stories
// ---------------------------------------------------------------------------

function RecurringBookingPrototype({ story }: { story: "create" | "createConflicts" | "scopes" | "lists" }) {
  if (story === "scopes") return <ScopePrototype />;
  if (story === "lists") return <SeriesInListsPrototype />;
  return <CreateRecurringPrototype initialRule={story === "createConflicts" ? CONFLICTING_RULE : DEFAULT_RULE} />;
}

const meta = {
  title: "Booking/Prototypes/Recurring Bookings (Fable 5.1)",
  component: RecurringBookingPrototype,
  parameters: { layout: "fullscreen" },
  beforeEach: async () => {
    await prototypeWorker.start({ onUnhandledFrame: "bypass", quiet: true });
    return () => prototypeWorker.stop();
  },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof RecurringBookingPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Create: Story = { args: { story: "create" } };
export const CreateWithConflicts: Story = { args: { story: "createConflicts" } };
export const CancelAndEditScopes: Story = { args: { story: "scopes" } };
export const SeriesInLists: Story = { args: { story: "lists" } };
