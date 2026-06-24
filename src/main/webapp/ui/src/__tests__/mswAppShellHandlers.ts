import { HttpResponse, http, type RequestHandler } from "msw";

/*
 * Default MSW handlers for the global app-shell endpoints that fire on render
 * regardless of which component is under test (current user, nav data,
 * analytics, live chat). Under Playwright these fire-and-forget requests were
 * harmless, but Vitest browser mode surfaces their rejections as test errors,
 * so we answer them with benign payloads. Registered as worker *defaults*
 * (survive `resetHandlers()`); individual suites can still override them.
 */
export const appShellHandlers = (): RequestHandler[] => [
  http.get("/api/v1/userDetails/whoami", () =>
    HttpResponse.json({
      id: 1,
      username: "user1a",
      firstName: "Test",
      lastName: "User",
      email: "user1a@researchspace.com",
      role: "ROLE_USER",
    }),
  ),
  http.get("/api/v1/userDetails/uiNavigationData", () =>
    HttpResponse.json({
      userDetails: {
        email: "user1a@researchspace.com",
        orcidId: null,
        orcidAvailable: false,
        fullName: "Test User",
        username: "user1a",
        profileImgSrc: null,
      },
      visibleTabs: {
        published: false,
        inventory: true,
        system: false,
        myLabGroups: true,
      },
      extraHelpLinks: [],
      bannerImgSrc: "",
      operatedAs: false,
      nextMaintenance: null,
    }),
  ),
  http.get("/session/ajax/analyticsProperties", () => HttpResponse.json({})),
  http.get("/session/ajax/livechatProperties", () => HttpResponse.json({})),
];
