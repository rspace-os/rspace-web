function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required env var ${name}. See src/__tests__/e2e/README.md for setup.`);
  }
  return value;
}

function optional(name: string, fallback: string): string {
  return process.env[name] ?? fallback;
}

export const env = {
  baseURL: optional("RSPACE_BASE_URL", "http://localhost:8080"),
  headless: optional("HEADLESS", "true") !== "false",
  ci: process.env.CI === "true",

  get sysadminUsername(): string {
    return optional("RSPACE_SYSADMIN_USERNAME", "sysadmin1");
  },
  get sysadminPassword(): string {
    return required("RSPACE_SYSADMIN_PASSWORD");
  },
  // UI tests use `appUser` from the USERS map (known seed credentials) — these
  // are only needed when explicitly targeting a non-seed user or for API auth.
  get testUsername(): string {
    return optional("RSPACE_TEST_USERNAME", "user1a");
  },
  get testPassword(): string {
    return optional("RSPACE_TEST_PASSWORD", "user1234");
  },
  get testApiKey(): string {
    return required("RSPACE_TEST_API_KEY");
  },
};
