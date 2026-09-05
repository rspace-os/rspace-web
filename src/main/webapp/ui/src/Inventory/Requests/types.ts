import type { LinkableRecord } from "../../stores/definitions/LinkableRecord";
import type { Person } from "../../stores/definitions/Person";

/**
 * Sample requests (biobank aspect 7, RPD-304): a researcher asks the owner of
 * a requestable sample for material; the owner reviews, produces the material
 * with an operation based on RSDEV-1231 and fulfils the request by
 * transferring ownership of the new sample, including its subsamples.
 *
 * ponytail: no backend yet, so these are UI-only shapes. Replace with the API
 * schema (valibot in `@/modules/inventory/queries`) when the endpoint lands.
 */
export type RequestStatus = "pending" | "approved" | "prepared" | "fulfilled" | "rejected";

export type ProcessOperation = "aliquot" | "derive";

export type Requester = Pick<Person, "id" | "username" | "fullName">;

export type SampleRequest = {
  /** Display identifier, e.g. `REQ-118`. Not a Global ID: requests have no record page. */
  id: string;
  requester: Requester;
  /** The item the request was raised against. Requests are listed across items on the Requests page. */
  sample: RequestableSample;
  note: string;
  /** Already formatted for display. */
  when: string;
  status: RequestStatus;
  rejectionReason?: string;
  /** Record produced by the process operation, once run. */
  produced?: LinkableRecord;
  operation?: ProcessOperation;
  operationPlan?: RequestOperationPlan;
  preparation?: RequestOperationPlan & { relation: "IsPartOf" | "IsDerivedFrom"; remaining: number };
};

export type RequestableSample = {
  name: string;
  globalId: string;
  owner: Requester;
  /** Prototype fixture quantities are all in mL. Operations consume subsamples, not the parent sample. */
  origins?: ReadonlyArray<{ globalId: string; quantity: number }>;
};

export type RequestOperationPlan = {
  originId: string;
  sampleName: string;
  processName: string;
  count: number;
  eachAmount: number;
  amountTaken: number;
};

export function requestOperationPlan(request: SampleRequest): RequestOperationPlan {
  return (
    request.operationPlan ?? {
      originId: request.sample.origins?.[0]?.globalId ?? "",
      sampleName: request.sample.name,
      processName: "",
      count: 1,
      eachAmount: 1,
      amountTaken: 1,
    }
  );
}

/** RSDEV-1231 operationValidation.ts rules for the prototype's single-origin, fixed-mL subset. */
export function validRequestOperation(request: SampleRequest, operation: ProcessOperation): boolean {
  const plan = requestOperationPlan(request);
  const origin = request.sample.origins?.find((item) => item.globalId === plan.originId);
  const storable = (amount: number) =>
    Number.isFinite(amount) && amount > 0 && Math.round(amount * 1000) / 1000 === amount;
  return Boolean(
    origin &&
      origin.quantity >= plan.amountTaken &&
      plan.sampleName.trim() &&
      (operation !== "derive" || plan.processName.trim()) &&
      Number.isInteger(plan.count) &&
      plan.count >= 1 &&
      plan.count <= 100 &&
      storable(plan.eachAmount) &&
      storable(plan.amountTaken),
  );
}

export type ActivityEntry = {
  id: string;
  kind: "raised" | "approved" | "produced" | "prepared" | "fulfilled" | "rejected" | "requestable";
  text: string;
  when: string;
};

/**
 * The notification system carries text and a link, not components, so a
 * notification only points at the request's page on the Requests list.
 */
export type RequestNotification = {
  id: string;
  text: string;
  /** e.g. `/inventory/requests/118` */
  link: string;
  when: string;
  unread: boolean;
};

/** Route of a request's page, e.g. `REQ-118` -> `/inventory/requests/118`. */
export function requestPath(requestId: string): string {
  return `/inventory/requests/${requestId.replace(/^REQ-/, "")}`;
}

/** RSDEV-1231: both Aliquot and Derive create a new sample containing new subsamples. */
export function producedRecord({
  id,
  name,
  recordTypeLabel,
}: {
  id: number;
  name: string;
  /** Translated record type name, e.g. `t("recordTypes.subsample.singular")`. */
  recordTypeLabel: string;
}): LinkableRecord {
  return {
    id,
    name,
    globalId: `SA${id}`,
    permalinkURL: `/inventory/sample/${id}`,
    iconName: "sample",
    recordTypeLabel,
  };
}

export function isRequestActionable(request: SampleRequest): boolean {
  return request.status === "pending" || request.status === "approved";
}
