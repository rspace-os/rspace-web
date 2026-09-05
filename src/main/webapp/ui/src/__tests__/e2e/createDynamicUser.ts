import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { alphaNumericUnique, DYNAMIC_USER_PASSWORD } from "@/__tests__/e2e/testData";

export type DynamicUser = { username: string; fullName: string; apiKey: string };

// Shared across specs and fixtures
export async function createDynamicUser(
  clientSysadmin: SysadminClient,
  role: "ROLE_USER" | "ROLE_PI" | "ROLE_ADMIN",
  namePrefix: string,
  lastName: string = namePrefix,
): Promise<DynamicUser> {
  const username = alphaNumericUnique(namePrefix);
  const apiKey = alphaNumericUnique("e2eApiKey");
  await clientSysadmin.createUser({
    username,
    password: DYNAMIC_USER_PASSWORD,
    email: `${username}@example.com`,
    firstName: "E2E",
    lastName,
    role,
    apiKey,
  });
  return { username, fullName: `E2E ${lastName}`, apiKey };
}
