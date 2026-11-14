import { HttpResponse, http } from "msw/http";
import currentUserFixture from "./fixtures/currentUser.json" with { type: "json" };
import infoFixture from "./fixtures/info.json" with { type: "json" };
import projectsFixture from "./fixtures/projects.json" with { type: "json" };
import questionnaireFixture from "./fixtures/questionnaire.json" with { type: "json" };

/*
 * The mock DSW server lives under a sub-path because the shared mock server already answers
 * GET /api for OMERO, which would otherwise shadow DSW's version info endpoint.
 */
export const DSW_MOCK_PATH = "/dsw";

/*
 * A pre-5.0.0 DSW server, which serves its API and version info under /wizard-api/. It has no
 * /api/ route, so the version check gets a 404 and RSpace falls back to the legacy paths.
 */
export const DSW_V4_MOCK_PATH = "/dsw-v4";

export const dswHandlers = [
  http.get(`${DSW_MOCK_PATH}/api/`, () => HttpResponse.json(infoFixture["v5.0.0"])),
  http.get(`${DSW_MOCK_PATH}/api/users/current`, () => HttpResponse.json(currentUserFixture)),
  http.get(`${DSW_MOCK_PATH}/api/projects`, () => HttpResponse.json(projectsFixture)),
  http.get(`${DSW_MOCK_PATH}/api/projects/:projectId/questionnaire`, () => HttpResponse.json(questionnaireFixture)),

  http.get(`${DSW_V4_MOCK_PATH}/wizard-api/`, () => HttpResponse.json(infoFixture["v4.35.0"])),
  http.get(`${DSW_V4_MOCK_PATH}/wizard-api/users/current`, () => HttpResponse.json(currentUserFixture)),
  http.get(`${DSW_V4_MOCK_PATH}/wizard-api/projects`, () => HttpResponse.json(projectsFixture)),
  http.get(`${DSW_V4_MOCK_PATH}/wizard-api/projects/:projectId/questionnaire`, () =>
    HttpResponse.json(questionnaireFixture),
  ),
];
