import { vi } from "vitest";

vi.mock("@/hooks/auth/useOauthToken", () => ({
  __esModule: true,
  default: () => ({
    getToken: async () => "token",
  }),
}));
