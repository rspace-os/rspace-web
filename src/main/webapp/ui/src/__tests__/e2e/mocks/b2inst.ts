import { HttpResponse, http } from "msw";

let nextRecordId = 1;

export const b2instHandlers = [
  http.get("/api/communities", () => HttpResponse.json({ hits: { hits: [] }, links: {} })),

  http.post("/api/records", () => {
    const id = `e2e-b2inst-${nextRecordId++}`;
    return HttpResponse.json(
      {
        id,
        isDraft: true,
        isPublished: false,
        status: "draft",
        revisionId: 1,
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
  // Field names are InvenioRDM's own snake_case, which is what B2instDraftRecord maps; the
  // create-draft handler above predates that and uses camelCase, so those keys deserialize to null.
  http.put("/api/records/:rid/draft", ({ params }) => {
    const rid = String(params.rid);
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

  http.delete("/api/records/:rid/draft", () => new HttpResponse(null, { status: 204 })),

  http.get("/api/records/:rid/draft/review", () => new HttpResponse(null, { status: 404 })),

  http.put("/api/records/:rid/draft/review", ({ params, request }) => {
    const url = new URL(request.url);
    return HttpResponse.json({
      isOpen: true,
      links: {
        actions: {
          submit: `${url.origin}/api/records/${params.rid}/actions/submit`,
        },
      },
    });
  }),

  http.post("/api/records/:rid/actions/submit", () => HttpResponse.json({ status: "submitted" })),
];
