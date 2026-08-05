import { HttpResponse, http } from "msw";
import loginResponseFixture from "./fixtures/loginResponse.json" with { type: "json" };
import projectsFixture from "./fixtures/projects.json" with { type: "json" };
import screensFixture from "./fixtures/screens.json" with { type: "json" };
import serversFixture from "./fixtures/servers.json" with { type: "json" };
import tokenFixture from "./fixtures/token.json" with { type: "json" };
import urlsFixture from "./fixtures/urls.json" with { type: "json" };
import versionFixture from "./fixtures/version.json" with { type: "json" };

function withRequestOrigin<T>(fixture: T, request: Request): T {
  const origin = new URL(request.url).origin;
  return JSON.parse(JSON.stringify(fixture).replaceAll("http://localhost:9099", origin)) as T;
}

export const omeroHandlers = [
  http.get("/api", ({ request }) => HttpResponse.json(withRequestOrigin(versionFixture, request))),

  http.get("/api/v0", ({ request }) => HttpResponse.json(withRequestOrigin(urlsFixture, request))),

  http.get("/api/v0/servers/", () => HttpResponse.json(serversFixture)),

  http.get("/api/v0/token/", () => HttpResponse.json(tokenFixture)),

  http.post("/api/v0/login/", () => HttpResponse.json(loginResponseFixture)),

  http.get("/api/v0/m/projects/", () => HttpResponse.json(projectsFixture)),

  http.get("/api/v0/m/screens/", () => HttpResponse.json(screensFixture)),
];
