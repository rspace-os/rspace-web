import { type SetupServer, setupServer } from "msw/node";

// A test that calls vi.resetModules() re-imports this module; keep the server setup.ts started.
const shared = globalThis as typeof globalThis & { mswServer?: SetupServer };
shared.mswServer ??= setupServer();
export const server = shared.mswServer;
