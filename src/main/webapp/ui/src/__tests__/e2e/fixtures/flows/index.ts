import { mergeTests } from "@playwright/test";
import { test as repositoryIntegrationTest } from "@/__tests__/e2e/fixtures/flows/repositoryIntegrations";
import { test as userSessionTest } from "@/__tests__/e2e/fixtures/flows/userSessions";

export type { SelfServicePiActor, UserSession } from "@/__tests__/e2e/fixtures/flows/userSessions";
export const test = mergeTests(repositoryIntegrationTest, userSessionTest);
