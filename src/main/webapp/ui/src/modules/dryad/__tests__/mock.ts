import { HttpResponse, http } from "msw";

export const dryadHandlers = [
  http.get("/dryad/oauth/authorize", ({ request }) => {
    const url = new URL(request.url);
    const redirectUri = url.searchParams.get("redirect_uri");
    if (!redirectUri) {
      return new HttpResponse("Missing redirect_uri", { status: 400 });
    }
    const target = new URL(redirectUri);
    target.searchParams.set("code", "mock-dryad-auth-code");
    const state = url.searchParams.get("state");
    if (state) target.searchParams.set("state", state);
    return HttpResponse.redirect(target.toString(), 302);
  }),

  http.post("/dryad/oauth/token", () =>
    HttpResponse.json({
      access_token: "mock-dryad-access-token",
      token_type: "bearer",
      expires_in: 3600,
    }),
  ),

  http.get("/dryad/api/v2/test", () => HttpResponse.json({ message: "Test connection OK", user_id: 424242 })),

  http.post("/dryad/api/v2/datasets", () =>
    HttpResponse.json({
      identifier: "doi:10.5061/dryad.mock1",
      editLink: "/stash/edit/dataset/doi%3A10.5061%2Fdryad.mock1",
    }),
  ),

  http.put("/dryad/api/v2/datasets/:doi/files/:filename", () => HttpResponse.json({})),
];
