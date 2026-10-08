import { mergeTests } from "@playwright/test";
import { test as documentSessionTest } from "@/__tests__/e2e/fixtures/flows/documentSessions";
import { test as publicSharingTest } from "@/__tests__/e2e/fixtures/flows/environment/publicSharing";
import { test as repositoryIntegrationTest } from "@/__tests__/e2e/fixtures/flows/environment/repositoryIntegrations";
import { test as messagingTest } from "@/__tests__/e2e/fixtures/flows/messaging";

export type { MessagingSession } from "@/__tests__/e2e/fixtures/flows/messaging";
export type { SelfServicePiActor, UserSession } from "@/__tests__/e2e/fixtures/flows/sessions/userSessions";
export const test = mergeTests(repositoryIntegrationTest, documentSessionTest, messagingTest, publicSharingTest);
