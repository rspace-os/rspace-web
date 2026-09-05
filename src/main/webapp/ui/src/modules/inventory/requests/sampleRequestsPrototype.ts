/**
 * Storybook-only state machine for the Sample Requests prototype. It stands in
 * for the backend so the owner and requester flows can be exercised end to end
 * in Storybook. Text here is fixture data, not UI copy: the components
 * translate their own labels.
 */
import type { FulfilmentStep } from "@/Inventory/Requests/FulfilmentDialogPrototype";
import {
  type ActivityEntry,
  type ProcessOperation,
  producedRecord,
  type RequestableSample,
  type Requester,
  type RequestNotification,
  type RequestOperationPlan,
  requestOperationPlan,
  requestPath,
  type SampleRequest,
  validRequestOperation,
} from "@/Inventory/Requests/types";

export const OWNER: Requester = { id: 1002, username: "dmarsh", fullName: "Dana Marsh" };
export const RACHEL: Requester = { id: 2291, username: "rokafor", fullName: "Rachel Okafor" };
const MEI: Requester = { id: 1877, username: "mlin", fullName: "Mei Lin" };
const SAMUEL: Requester = { id: 2044, username: "sboateng", fullName: "Samuel Boateng" };

export const SAMPLE: RequestableSample = {
  name: "Anti-CD3 antibody (clone OKT3)",
  globalId: "SA4021",
  owner: OWNER,
  origins: [
    { globalId: "SS4022", quantity: 10 },
    { globalId: "SS4023", quantity: 5 },
  ],
};

/** Owned by Mei; Dana has an outgoing request against it. */
export const PBMC_SAMPLE: RequestableSample = {
  name: "Donor PBMC vial · D-114",
  globalId: "SA3980",
  owner: MEI,
  origins: [{ globalId: "SS3981", quantity: 3 }],
};

export const OTHER_SAMPLES: ReadonlyArray<{ name: string; globalId: string; requestable: boolean }> = [
  { name: SAMPLE.name, globalId: SAMPLE.globalId, requestable: true },
  { name: "Recombinant IL-2", globalId: "SA4009", requestable: false },
  { name: PBMC_SAMPLE.name, globalId: PBMC_SAMPLE.globalId, requestable: true },
  { name: "HEK293T working stock", globalId: "SA3902", requestable: false },
];

const PRODUCED_ID = 4088;

export const PENDING_REQUEST: SampleRequest = {
  id: "REQ-118",
  sample: SAMPLE,
  requester: RACHEL,
  note: "Two 1 mL aliquots of the lot B-2411 antibody for a flow panel titration, six conditions in duplicate.",
  when: "2 hours ago",
  status: "pending",
};

export const FULFILLED_REQUEST: SampleRequest = {
  id: "REQ-112",
  sample: SAMPLE,
  requester: MEI,
  note: "Bridging study against the new assay plate.",
  when: "21 Aug",
  status: "fulfilled",
  operation: "aliquot",
  produced: producedRecord({
    id: 4076,
    name: "Anti-CD3 antibody, aliquot for Mei Lin",
    recordTypeLabel: "Sample",
  }),
};

/** What REQ-118 produces once the aliquot operation has run. */
export const PRODUCED_FOR_RACHEL = producedRecord({
  id: PRODUCED_ID,
  name: "Anti-CD3 antibody, aliquot for Rachel Okafor",
  recordTypeLabel: "Sample",
});

export const REJECTED_REQUEST: SampleRequest = {
  id: "REQ-104",
  sample: SAMPLE,
  requester: SAMUEL,
  note: "Six vials for a screening run.",
  when: "2 Aug",
  status: "rejected",
  rejectionReason: "Over the volume the biobank can release from this lot; resubmit for 2 vials.",
};

/** Dana's own request, against Mei's sample: what the "My requests" tab lists. */
export const OUTGOING_REQUEST: SampleRequest = {
  id: "REQ-117",
  sample: PBMC_SAMPLE,
  requester: OWNER,
  note: "One vial for a viability check before the cohort thaw.",
  when: "Yesterday",
  status: "pending",
};

export const DEFAULT_REQUESTS: ReadonlyArray<SampleRequest> = [
  PENDING_REQUEST,
  FULFILLED_REQUEST,
  REJECTED_REQUEST,
  OUTGOING_REQUEST,
];

export const DEFAULT_ACTIVITY: ReadonlyArray<ActivityEntry> = [
  { id: "a1", kind: "raised", text: "REQ-118 raised by rokafor against SA4021", when: "2 hours ago" },
  { id: "a2", kind: "fulfilled", text: "REQ-112 fulfilled. SA4076 ownership transferred to mlin", when: "21 Aug" },
  { id: "a3", kind: "rejected", text: "REQ-104 rejected by dmarsh. Reason recorded", when: "2 Aug" },
  { id: "a4", kind: "requestable", text: "SA4021 made requestable by dmarsh", when: "18 Aug" },
];

/**
 * Notifications are plain text with a link; the notification system cannot
 * render components. `for` says which persona's list the entry appears in.
 */
export type NotificationData = RequestNotification & { for: "owner" | "requester"; requestId: string };

function notification(
  request: SampleRequest,
  forPersona: NotificationData["for"],
  text: string,
  when = "Just now",
): NotificationData {
  return {
    id: `${forPersona}-${request.id}-${request.status}`,
    for: forPersona,
    requestId: request.id,
    text,
    link: requestPath(request.id),
    when,
    unread: true,
  };
}

function raisedNotification(request: SampleRequest): NotificationData {
  return notification(
    request,
    "owner",
    `${request.requester.fullName} (${request.requester.username}) requested material from ${request.sample.globalId} ${request.sample.name}: “${request.note}” (${request.id})`,
    request.when,
  );
}

export type PrototypeState = {
  requestable: boolean;
  requests: ReadonlyArray<SampleRequest>;
  activity: ReadonlyArray<ActivityEntry>;
  notifications: ReadonlyArray<NotificationData>;
  active: { requestId: string; step: FulfilmentStep } | null;
  toast: string | null;
};

export type PrototypeAction =
  | { type: "prepareRequest"; request: SampleRequest; operation: ProcessOperation; producedRecordTypeLabel: string }
  | { type: "toggleRequestable" }
  | { type: "submitRequest"; note: string }
  | { type: "openRequest"; requestId: string }
  | { type: "approve" }
  | { type: "setOperation"; operation: ProcessOperation }
  | { type: "setOperationPlan"; plan: RequestOperationPlan }
  | { type: "runOperation"; producedRecordTypeLabel: string }
  | { type: "transfer"; requestId: string }
  | { type: "reject"; reason: string }
  | { type: "closeDialog" }
  | { type: "clearToast" };

export function initialState({
  requestable = true,
  requests = DEFAULT_REQUESTS,
  operation = "aliquot",
}: {
  requestable?: boolean;
  requests?: ReadonlyArray<SampleRequest>;
  operation?: ProcessOperation;
}): PrototypeState {
  return {
    requestable,
    requests: requests.map((request) => ({ ...request, operation: request.operation ?? operation })),
    // keep only the audit entries whose request is part of this fixture
    activity: DEFAULT_ACTIVITY.filter(
      (e) => !e.text.startsWith("REQ-") || requests.some((r) => e.text.startsWith(r.id)),
    ),
    notifications: requests
      .filter((r) => r.status === "pending" && r.sample.owner.id === OWNER.id)
      .map(raisedNotification),
    active: null,
    toast: null,
  };
}

/** Shared scenarios for the single prototype. Switching scenarios explicitly resets mock state. */
export function scenarioState(scenario: "standard" | "new" | "stress" | "empty"): PrototypeState {
  if (scenario === "empty") return initialState({ requests: [] });
  if (scenario === "new")
    return initialState({ requests: DEFAULT_REQUESTS.filter((request) => request.id !== PENDING_REQUEST.id) });
  if (scenario !== "stress") return initialState({});
  const requests = Array.from({ length: 100 }, (_, index) => ({
    ...PENDING_REQUEST,
    id: `REQ-${1000 + index}`,
    sample: {
      ...SAMPLE,
      name: `${"Cryopreserved peripheral blood mononuclear cells / longitudinal immune-response validation cohort / ".repeat(4)} specimen ${index + 1}`,
    },
    requester: {
      ...RACHEL,
      fullName: "Dr Alexandra-Maria de la Cruz van der Meer, Translational Immunology and Cell Therapy Research Group",
    },
    note: `${"Retain donor consent restrictions, chain of custody and temperature history. ".repeat(25)} BATCH_${"ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".repeat(6)}`,
  }));
  const kinds: ActivityEntry["kind"][] = ["raised", "approved", "prepared", "fulfilled", "rejected"];
  return {
    ...initialState({ requests }),
    activity: Array.from({ length: 500 }, (_, index) => ({
      id: `event-${index + 1}`,
      kind: kinds[index % kinds.length],
      text: `Event ${index + 1} · ${requests[index % requests.length].id} · historical workflow event · ${requests[index % requests.length].note}`,
      when: `2026-09-${String(6 - Math.floor(index / 100)).padStart(2, "0")} 10:00 UTC`,
    })),
  };
}

function withActivity(state: PrototypeState, ...entries: Array<Omit<ActivityEntry, "id" | "when">>): PrototypeState {
  return {
    ...state,
    activity: [
      ...entries.map((entry, index) => ({ ...entry, id: `a-${state.activity.length + index}`, when: "Just now" })),
      ...state.activity,
    ],
  };
}

function updateRequest(
  state: PrototypeState,
  requestId: string,
  patch: Partial<SampleRequest>,
): ReadonlyArray<SampleRequest> {
  return state.requests.map((r) => (r.id === requestId ? { ...r, ...patch } : r));
}

function activeRequest(state: PrototypeState): SampleRequest | null {
  return state.requests.find((r) => r.id === state.active?.requestId) ?? null;
}

function nextRequestId(state: PrototypeState): string {
  return `REQ-${Math.max(118, ...state.requests.map((request) => Number(request.id.replace("REQ-", "")) || 0)) + 1}`;
}

export function reducer(state: PrototypeState, action: PrototypeAction): PrototypeState {
  switch (action.type) {
    case "prepareRequest": {
      // Owner-entered request + preparation is one mock commit. Cancelling the wizard dispatches nothing.
      const sample =
        state.requests.find((r) => r.sample.globalId === action.request.sample.globalId)?.sample ??
        action.request.sample;
      const request = {
        ...action.request,
        sample,
        id: nextRequestId(state),
        operation: action.operation,
        status: "pending" as const,
        when: "Just now",
      };
      if (!request.note.trim() || !validRequestOperation(request, action.operation)) return state;
      const added = withActivity(
        { ...state, requests: [request, ...state.requests] },
        {
          kind: "raised",
          text: `${request.id} recorded by ${OWNER.username} on behalf of ${request.requester.username} against ${sample.globalId}`,
        },
      );
      const approved = reducer(reducer(added, { type: "openRequest", requestId: request.id }), { type: "approve" });
      return reducer(approved, { type: "runOperation", producedRecordTypeLabel: action.producedRecordTypeLabel });
    }
    case "toggleRequestable": {
      const requestable = !state.requestable;
      return withActivity(
        {
          ...state,
          requestable,
          toast: requestable ? `${SAMPLE.globalId} is now requestable` : `${SAMPLE.globalId} is no longer requestable`,
        },
        {
          kind: "requestable",
          text: `${SAMPLE.globalId} made ${requestable ? "requestable" : "not requestable"} by ${OWNER.username}`,
        },
      );
    }
    case "submitRequest": {
      const request: SampleRequest = {
        id: nextRequestId(state),
        sample: state.requests.find((request) => request.sample.globalId === SAMPLE.globalId)?.sample ?? SAMPLE,
        requester: RACHEL,
        note: action.note.trim() || "Two 1 mL aliquots for a flow panel titration.",
        when: "Just now",
        status: "pending",
      };
      return withActivity(
        {
          ...state,
          requests: [request, ...state.requests],
          notifications: [raisedNotification(request), ...state.notifications],
          toast: "Request sent to the sample owner",
        },
        { kind: "raised", text: `${request.id} raised by ${RACHEL.username} against ${SAMPLE.globalId}` },
      );
    }
    case "openRequest": {
      const request = state.requests.find((r) => r.id === action.requestId);
      if (!request) return state;
      const step: FulfilmentStep =
        request.status === "approved" ? "operation" : request.status === "pending" ? "review" : "done";
      return {
        ...state,
        active: { requestId: request.id, step },
        notifications: state.notifications.map((n) => (n.requestId === request.id ? { ...n, unread: false } : n)),
      };
    }
    case "approve": {
      const request = activeRequest(state);
      if (request?.status !== "pending") return state;
      return withActivity(
        {
          ...state,
          active: { requestId: request.id, step: "operation" },
          requests: updateRequest(state, request.id, { status: "approved" }),
        },
        { kind: "approved", text: `${request.id} approved by ${OWNER.username}` },
      );
    }
    case "setOperation": {
      const request = activeRequest(state);
      if (request?.status !== "approved") return state;
      return {
        ...state,
        requests: updateRequest(state, request.id, { operation: action.operation }),
      };
    }
    case "setOperationPlan": {
      const request = activeRequest(state);
      if (request?.status !== "approved") return state;
      return { ...state, requests: updateRequest(state, request.id, { operationPlan: action.plan }) };
    }
    case "runOperation": {
      const request = activeRequest(state);
      const operation = request?.operation ?? "aliquot";
      if (request?.status !== "approved" || !validRequestOperation(request, operation)) return state;
      const plan = requestOperationPlan(request);
      const origin = request.sample.origins?.find((item) => item.globalId === plan.originId);
      if (!origin) return state;
      const relation = operation === "aliquot" ? "IsPartOf" : "IsDerivedFrom";
      const remaining = Math.round((origin.quantity - plan.amountTaken) * 1000) / 1000;
      const nextId = Math.max(PRODUCED_ID - 1, ...state.requests.map((r) => r.produced?.id ?? 0)) + 1;
      const produced = producedRecord({
        id: nextId,
        name: plan.sampleName.trim(),
        recordTypeLabel: action.producedRecordTypeLabel,
      });
      return withActivity(
        {
          ...state,
          active: { requestId: request.id, step: "done" },
          // One mock transaction, matching the operation endpoint: output and decrement succeed together.
          requests: state.requests.map((r) => ({
            ...r,
            ...(r.id === request.id
              ? {
                  status: "prepared" as const,
                  produced,
                  operation,
                  preparation: { ...plan, relation, remaining },
                }
              : {}),
            sample: {
              ...r.sample,
              origins: r.sample.origins?.map((item) =>
                item.globalId === origin.globalId ? { ...item, quantity: remaining } : item,
              ),
            },
          })),
          notifications: state.notifications.filter((n) => n.requestId !== request.id),
        },
        {
          kind: "prepared",
          text: `Material prepared for ${request.id} by ${OWNER.username}. Awaiting ownership transfer`,
        },
        {
          kind: "produced",
          text: `${produced.globalId} created for ${request.id} with ${plan.count} subsamples of ${plan.eachAmount} mL. ${relation} ${origin.globalId} on the new sample. ${plan.amountTaken} mL consumed; ${remaining} mL remains.${operation === "derive" ? ` Process: ${plan.processName}.` : ""}`,
        },
      );
    }
    case "transfer": {
      const request = state.requests.find((r) => r.id === action.requestId);
      if (!request?.produced || request.status !== "prepared") return state;
      const produced = request.produced;
      return withActivity(
        {
          ...state,
          requests: updateRequest(state, request.id, { status: "fulfilled" }),
          notifications: [
            notification(
              { ...request, status: "fulfilled" },
              "requester",
              `${request.id} has been fulfilled. ${OWNER.fullName} produced ${produced.name} (${produced.globalId}) from ${request.sample.globalId} and transferred the sample and all its subsamples to you.`,
            ),
            ...state.notifications.filter((n) => n.requestId !== request.id),
          ],
          toast: `${request.id} fulfilled. ${produced.globalId} now owned by ${request.requester.fullName}`,
        },
        {
          kind: "fulfilled",
          text: `${request.id} fulfilled. ${produced.globalId} ownership transferred to ${request.requester.username}`,
        },
      );
    }
    case "reject": {
      const request = activeRequest(state);
      if (request?.status !== "pending") return state;
      return withActivity(
        {
          ...state,
          // stay on the request so the rejection is visible; the record's dialog closes itself
          active: { requestId: request.id, step: "review" },
          requests: updateRequest(state, request.id, { status: "rejected", rejectionReason: action.reason }),
          notifications: [
            notification(
              { ...request, status: "rejected" },
              "requester",
              `${request.id} against ${SAMPLE.globalId} was rejected by ${OWNER.fullName}: “${action.reason}”`,
            ),
            ...state.notifications.filter((n) => n.requestId !== request.id),
          ],
          toast: `${request.id} rejected. Requester notified`,
        },
        { kind: "rejected", text: `${request.id} rejected by ${OWNER.username}. Reason recorded` },
      );
    }
    case "closeDialog":
      return { ...state, active: null };
    case "clearToast":
      return { ...state, toast: null };
    default:
      return state;
  }
}
