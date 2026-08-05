import { HttpResponse, http } from "msw";
import currentUserFixture from "./fixtures/currentUser.json" with { type: "json" };
import projectsFixture from "./fixtures/projects.json" with { type: "json" };
import questionnaireFixture from "./fixtures/questionnaire.json" with { type: "json" };

export const dswHandlers = [
  http.get("/wizard-api/users/current", () => HttpResponse.json(currentUserFixture)),
  http.get("/wizard-api/projects", () => HttpResponse.json(projectsFixture)),
  http.get("/wizard-api/projects/:projectId/questionnaire", () => HttpResponse.json(questionnaireFixture)),
];
