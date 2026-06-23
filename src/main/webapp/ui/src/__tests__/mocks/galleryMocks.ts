import { HttpResponse, http, type RequestHandler } from "msw";

/*
 * Shared MSW handlers for the gallery-render endpoints that fire when any
 * gallery component is mounted. Register these per-suite via
 * `worker.use(...galleryAppShellHandlers())` in a `beforeEach` — they are NOT
 * global defaults because they are too gallery-specific to live in the app-shell
 * defaults in mswAppShellHandlers.ts.
 *
 * Note: `analyticsProperties` and `livechatProperties` are already covered by
 * the global app-shell defaults in mswAppShellHandlers.ts. Do not duplicate
 * them here.
 *
 * Note: the inventory OAuth token handler is in inventoryMocks.ts — import
 * `oauthTokenHandler` from there when a gallery suite also needs it.
 */
export const galleryAppShellHandlers = (): RequestHandler[] => [
  /*
   * UiPreferences fetches one request per key — catch the whole path prefix
   * with a wildcard and return an empty object (the component's defaults apply).
   */
  http.get("/userform/ajax/preference*", () => HttpResponse.json({})),

  /*
   * Deployment properties — the gallery reads several boolean flags; returning
   * `false` disables every optional feature so the component renders its
   * minimal form.
   */
  http.get("/deploymentproperties/ajax/property*", () => HttpResponse.json(false)),

  /*
   * SVG icon assets — Vite serves them as real files in the app but they are
   * not available from the Vitest server root. Return a minimal valid SVG so
   * <img> and inline-SVG usages do not produce broken-image errors.
   */
  http.get(
    "**/*.svg",
    () =>
      new HttpResponse(
        `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><rect width="24" height="24" fill="none"/></svg>`,
        {
          status: 200,
          headers: { "Content-Type": "image/svg+xml" },
        },
      ),
  ),

  /*
   * Supported file-extension map — used by the gallery info panel and file
   * cards. Return an empty object so every extension falls back gracefully.
   */
  http.get("/*/supportedExts", () => HttpResponse.json({})),

  /*
   * Gallery file listing — fetched by `useGalleryListing` for each visible
   * folder tree node. Return an empty listing so folder expansion attempts
   * gracefully show "no children" rather than surfacing an "Error retrieving
   * gallery files." alert toast. Without this mock, every `TreeItemContent`
   * that calls `useGalleryListing` for a folder's children will get a 404,
   * catch it, and add an error-variant alert that intercepts pointer events.
   */
  http.get("/gallery/getUploadedFiles", () =>
    HttpResponse.json({
      data: {
        items: {
          totalHits: 0,
          totalPages: 1,
          results: [],
        },
        parentId: 0,
      },
      error: null,
      success: true,
      errorMsg: null,
    }),
  ),

  /*
   * Gallery thumbnail images — the tree view renders MUI Avatar with the
   * thumbnail URL as its `src`. Return a minimal 1×1 PNG so the image request
   * does not 404 and no broken-image indicators appear.
   */
  http.get(
    "/gallery/getThumbnail/*/*",
    () =>
      new HttpResponse(
        Uint8Array.from(
          atob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mP8//8/AwAI/wH+9Q4AAAAASUVORK5CYII="),
          (c) => c.charCodeAt(0),
        ).buffer,
        {
          status: 200,
          headers: { "Content-Type": "image/png" },
        },
      ),
  ),
];
