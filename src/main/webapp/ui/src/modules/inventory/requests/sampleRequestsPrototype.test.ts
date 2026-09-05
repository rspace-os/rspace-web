import { describe, expect, it } from "vitest";
import { requestOperationPlan } from "@/Inventory/Requests/types";
import { FULFILLED_REQUEST, initialState, PENDING_REQUEST, reducer, scenarioState } from "./sampleRequestsPrototype";

const run = { type: "runOperation", producedRecordTypeLabel: "Sample" } as const;
const approved = () =>
  reducer(reducer(initialState({}), { type: "openRequest", requestId: "REQ-118" }), { type: "approve" });

describe("RSDEV-1231 request preparation", () => {
  it("allocates distinct IDs and preserves stock across wizard and researcher submissions", () => {
    const prepared = reducer(scenarioState("new"), {
      type: "prepareRequest",
      request: { ...PENDING_REQUEST, requester: FULFILLED_REQUEST.requester },
      operation: "aliquot",
      producedRecordTypeLabel: "Sample",
    });
    const submitted = reducer(prepared, { type: "submitRequest", note: "Rachel's follow-up" });
    expect(submitted.requests[0].id).toBe("REQ-120");
    expect(new Set(submitted.requests.map((request) => request.id)).size).toBe(submitted.requests.length);
    expect(submitted.requests[0].sample.origins?.[0].quantity).toBe(9);
    let next = reducer(reducer(submitted, { type: "openRequest", requestId: "REQ-120" }), { type: "approve" });
    expect(next.requests.find((request) => request.id === "REQ-119")?.status).toBe("prepared");
    next = reducer(next, {
      type: "setOperationPlan",
      plan: { ...requestOperationPlan(next.requests[0]), amountTaken: 10 },
    });
    expect(reducer(next, run)).toBe(next);
  });

  it("keeps operation choices on their own requests", () => {
    let state = reducer(approved(), { type: "setOperation", operation: "derive" });
    state = reducer(state, { type: "submitRequest", note: "Another request" });
    state = reducer(reducer(state, { type: "openRequest", requestId: "REQ-119" }), { type: "approve" });
    const prepared = reducer(state, run);
    expect(prepared.requests[0]).toMatchObject({ status: "prepared", operation: "aliquot" });
    expect(prepared.requests.find((request) => request.id === "REQ-118")?.operation).toBe("derive");
  });

  it("commits an operator-entered request with preparation only when the whole draft is valid", () => {
    const state = initialState({});
    const action = {
      type: "prepareRequest",
      request: PENDING_REQUEST,
      operation: "aliquot",
      producedRecordTypeLabel: "Sample",
    } as const;
    expect(reducer(state, { ...action, request: { ...PENDING_REQUEST, note: " " } })).toBe(state);
    expect(
      reducer(state, {
        ...action,
        request: { ...PENDING_REQUEST, operationPlan: { ...requestOperationPlan(PENDING_REQUEST), amountTaken: 11 } },
      }),
    ).toBe(state);
    const next = reducer(state, action);
    expect(new Set(next.activity.map((entry) => entry.id)).size).toBe(next.activity.length);
    expect(next.requests[0]).toMatchObject({
      id: "REQ-119",
      status: "prepared",
      requester: PENDING_REQUEST.requester,
      produced: { globalId: "SA4088" },
    });
    expect(next.requests[0].sample.origins?.[0].quantity).toBe(9);
    expect(state.requests[0].sample.origins?.[0].quantity).toBe(10);
  });
  it("creates a sample, decrements the shared origin atomically, and transfers separately", () => {
    const state = reducer(approved(), {
      type: "setOperationPlan",
      plan: { ...requestOperationPlan(PENDING_REQUEST), count: 2, eachAmount: 1.001, amountTaken: 2 },
    });
    const prepared = reducer(state, run);
    expect(prepared.requests[0]).toMatchObject({
      status: "prepared",
      produced: { globalId: "SA4088", iconName: "sample" },
      preparation: { count: 2, eachAmount: 1.001, amountTaken: 2, relation: "IsPartOf", remaining: 8 },
    });
    expect(prepared.requests.slice(0, 3).map((r) => r.sample.origins?.[0].quantity)).toEqual([8, 8, 8]);
    expect(state.requests[0].sample.origins?.[0].quantity).toBe(10);
    expect(reducer(prepared, run)).toBe(prepared);
    expect(reducer(prepared, { type: "transfer", requestId: "REQ-118" }).requests[0].status).toBe("fulfilled");
  });

  it.each([
    { amountTaken: 11 },
    { amountTaken: 0 },
    { amountTaken: 0.0004 },
    { eachAmount: 0 },
    { count: 1.5 },
    { count: 101 },
    { originId: "SS-missing" },
    { sampleName: " " },
  ])("rejects invalid input without partial effects: %o", (patch) => {
    const state = reducer(approved(), {
      type: "setOperationPlan",
      plan: { ...requestOperationPlan(PENDING_REQUEST), ...patch },
    });
    expect(reducer(state, run)).toBe(state);
  });

  it("requires a process for Derive and links the new sample to its origin subsample", () => {
    const derive = reducer(approved(), { type: "setOperation", operation: "derive" });
    expect(reducer(derive, run)).toBe(derive);
    const ready = reducer(derive, {
      type: "setOperationPlan",
      plan: { ...requestOperationPlan(PENDING_REQUEST), processName: "Dilution" },
    });
    expect(reducer(ready, run).requests[0]).toMatchObject({
      produced: { globalId: "SA4088" },
      preparation: { relation: "IsDerivedFrom", originId: "SS4022", processName: "Dilution" },
    });
  });

  it("does not prepare an unapproved request or transfer unprepared material", () => {
    const state = reducer(initialState({}), { type: "openRequest", requestId: "REQ-118" });
    expect(reducer(state, run)).toBe(state);
    expect(reducer(state, { type: "transfer", requestId: "REQ-118" })).toBe(state);
  });

  it("rechecks remaining material for another request and gives each output a distinct ID", () => {
    const first = reducer(approved(), run);
    const next = {
      ...first,
      requests: [
        ...first.requests,
        {
          ...first.requests[0],
          id: "REQ-120",
          status: "pending" as const,
          produced: undefined,
          preparation: undefined,
        },
      ],
    };
    let second = reducer(reducer(next, { type: "openRequest", requestId: "REQ-120" }), { type: "approve" });
    second = reducer(second, {
      type: "setOperationPlan",
      plan: { ...requestOperationPlan(PENDING_REQUEST), amountTaken: 10 },
    });
    expect(reducer(second, run)).toBe(second);
    second = reducer(second, {
      type: "setOperationPlan",
      plan: { ...requestOperationPlan(PENDING_REQUEST), amountTaken: 9 },
    });
    const result = reducer(second, run);
    expect(result.requests.at(-1)).toMatchObject({ produced: { globalId: "SA4089" }, preparation: { remaining: 0 } });
  });
});
