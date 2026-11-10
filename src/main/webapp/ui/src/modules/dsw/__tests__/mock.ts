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

export const dswHandlers = [
  http.get(`${DSW_MOCK_PATH}/api/`, () => HttpResponse.json(infoFixture)),
  http.get(`${DSW_MOCK_PATH}/api/users/current`, () => HttpResponse.json(currentUserFixture)),
  http.get(`${DSW_MOCK_PATH}/api/projects`, () => HttpResponse.json(projectsFixture)),
  http.get(`${DSW_MOCK_PATH}/api/projects/:projectId/questionnaire`, () => HttpResponse.json(questionnaireFixture)),
];
