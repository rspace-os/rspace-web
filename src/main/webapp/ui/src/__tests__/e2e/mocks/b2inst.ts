import { HttpResponse, http } from "msw";

let nextRecordId = 1;

/** Any id starting with this is a "published" record - a prefix, not one fixed id, so parallel runs never collide. */
export const IMPORTABLE_RID_PREFIX = "e2e-pidinst-import";

/** A Handle this mock will resolve, built from a record id `getRecordByHandle` can look up. */
export function importableHandle(rid: string): string {
  return `21.T11998/${rid}`;
}

/** The ePIC PID a published record carries in `pids.epic.identifier`, which Refresh stores as the public URL. */
export function mintedHandleUrl(rid: string): string {
  return `http://hdl.handle.net/${importableHandle(rid)}`;
}

function fixtureB2instRecord(rid: string, name: string) {
  return {
    id: rid,
    is_draft: false,
    is_published: true,
    status: "published",
    metadata: {
      Name: name,
      Identifier: { identifierType: "Handle", identifierValue: importableHandle(rid) },
      Owner: [{ ownerName: "E2E Test Institution" }],
      Manufacturer: [{ manufacturerName: "E2E Instrument Co" }],
    },
    pids: { epic: { identifier: mintedHandleUrl(rid) } },
    links: { self_html: `https://b2inst-test.gwdg.de/records/${rid}` },
  };
}

/** Stand-in for a curator's decision, which nothing here can otherwise fast-forward. */
const reviewStatusByRid = new Map<string, string>();

/**
 * A record this deployment "minted" and B2INST later accepted: not one of the review flow's own
 * drafts (those never reach `is_published`), so it needs its own map rather than reusing
 * `fixtureB2instRecord`'s static content.
 */
const acceptedRecords = new Map<string, ReturnType<typeof fixtureB2instRecord>>();

/** Receipt for a silent push - proof it happened, since the UI shows nothing when it does. */
const lastDraftUpdateByRid = new Map<string, unknown>();

export const b2instHandlers = [
  http.get("/api/communities", () => HttpResponse.json({ hits: { hits: [] }, links: {} })),

  http.get("/api/records/:rid", ({ params }) => {
    const rid = String(params.rid);
    if (rid.startsWith(IMPORTABLE_RID_PREFIX)) {
      return HttpResponse.json(fixtureB2instRecord(rid, `E2E Import Target ${rid}`));
    }
    const accepted = acceptedRecords.get(rid);
    if (accepted) {
      return HttpResponse.json(accepted);
    }
    return new HttpResponse(null, { status: 404 });
  }),

  // Puppet strings for a curator, not part of the real B2INST API.
  http.put("/__e2e/b2inst/review-status/:rid", async ({ params, request }) => {
    const rid = String(params.rid);
    const { status } = (await request.json()) as { status: string };
    reviewStatusByRid.set(rid, status);
    if (status === "accepted") {
      acceptedRecords.set(rid, fixtureB2instRecord(rid, "E2E Refresh Test Instrument"));
    }
    return new HttpResponse(null, { status: 204 });
  }),

  // Field names are InvenioRDM's own snake_case, which is what B2instDraftRecord maps. Anything
  // spelled camelCase here deserializes to null on the RSpace side without any error, so the stub
  // would answer a shape the real provider never sends.
  http.post("/api/records", () => {
    const id = `e2e-b2inst-${nextRecordId++}`;
    return HttpResponse.json(
      {
        id,
        is_draft: true,
        is_published: false,
        status: "draft",
        revision_id: 1,
        links: {
          self: `/api/records/${id}/draft`,
          self_html: `/uploads/${id}`,
        },
      },
      { status: 201 },
    );
  }),

  // The on-save external metadata update (RSDEV-1251): a full-replace PUT of the draft. Without
  // this handler the mock server answers 404 and every instrument save that carries a B2INST draft
  // silently reports a failed push, which no assertion would catch because the toast is generic.
  http.put("/api/records/:rid/draft", async ({ params, request }) => {
    const rid = String(params.rid);
    lastDraftUpdateByRid.set(rid, await request.json());
    return HttpResponse.json({
      id: rid,
      is_draft: true,
      is_published: false,
      status: "draft",
      revision_id: 2,
      links: {
        self: `/api/records/${rid}/draft`,
        self_html: `/uploads/${rid}`,
      },
    });
  }),

  // Opens the receipt above; never called by RSpace itself.
  http.get("/__e2e/b2inst/draft-update/:rid", ({ params }) => {
    const body = lastDraftUpdateByRid.get(String(params.rid));
    if (!body) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(body);
  }),

  http.delete("/api/records/:rid/draft", () => new HttpResponse(null, { status: 204 })),

  http.get("/api/records/:rid/draft/review", ({ params }) => {
    const status = reviewStatusByRid.get(String(params.rid));
    if (!status) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json({ status, is_open: status === "created" || status === "submitted" });
  }),

  http.put("/api/records/:rid/draft/review", ({ params, request }) => {
    const rid = String(params.rid);
    reviewStatusByRid.set(rid, "created");
    const url = new URL(request.url);
    return HttpResponse.json({
      status: "created",
      is_open: true,
      links: {
        actions: {
          submit: `${url.origin}/api/records/${rid}/actions/submit`,
        },
      },
    });
  }),

  http.post("/api/records/:rid/actions/submit", ({ params }) => {
    reviewStatusByRid.set(String(params.rid), "submitted");
    return HttpResponse.json({ status: "submitted" });
  }),
];
