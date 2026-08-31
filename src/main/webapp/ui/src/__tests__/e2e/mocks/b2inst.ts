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
