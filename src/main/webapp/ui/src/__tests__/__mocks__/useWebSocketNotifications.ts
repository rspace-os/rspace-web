import { vi } from "vitest";

vi.mock("@/hooks/websockets/useWebSocketNotifications", () => ({
  __esModule: true,
  default: () => ({
    notificationCount: 0,
    messageCount: 0,
    specialMessageCount: 0,
  }),
}));
