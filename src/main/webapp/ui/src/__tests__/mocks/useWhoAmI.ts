import { vi } from "vitest";

vi.mock("@/hooks/api/useWhoAmI", () => ({
  __esModule: true,
  default: () => ({
    tag: "success",
    value: {
      id: 1,
      username: "test",
      firstName: "Test",
      lastName: "User",
      hasPiRole: false,
      hasSysAdminRole: false,
      email: "test@example.com",
      bench: null,
      workbenchId: null,
      getBench: () =>
        Promise.reject(
          new Error("Not implemented by this Person implementation"),
        ),
      isCurrentUser: true,
      fullName: "Test User",
      label: "Test User (test)",
    },
  }),
}));
