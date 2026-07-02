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
    return optional("RSPACE_SYSADMIN_PASSWORD", "sysWisc23!");
  },
  get sysadminApiKey(): string {
    return optional("RSPACE_SYSADMIN_API_KEY", "abcdefghijklmnop12");
  },
};
