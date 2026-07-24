import { describe, expect, it } from "vitest";
import { createUserSessionStore } from "./userSessionStore";

describe("userSessionStore", () => {
  it("refreshes an existing session without duplicating it", () => {
    const store = createUserSessionStore();

    store.getState().upsertSession({ accountId: "1", token: "stale" });
    store.getState().upsertSession({ accountId: "1", token: "fresh" });

    expect(store.getState().sessions).toEqual([{ accountId: "1", token: "fresh" }]);
  });

  it("switches between registered accounts and ignores unknown ones", () => {
    const store = createUserSessionStore();

    store.getState().upsertSession({ accountId: "1", token: "token-1" });
    store.getState().upsertSession({ accountId: "2", token: "token-2" });

    store.getState().switchAccount("1");
    expect(store.getState().activeAccountId).toBe("1");

    store.getState().switchAccount("unknown");
    expect(store.getState().activeAccountId).toBe("1");
  });

  it("falls back to a remaining session when the active one is removed", () => {
    const store = createUserSessionStore();

    store.getState().upsertSession({ accountId: "1", token: "token-1" });
    store.getState().upsertSession({ accountId: "2", token: "token-2" });

    store.getState().removeSession("2");

    expect(store.getState()).toMatchObject({
      sessions: [{ accountId: "1", token: "token-1" }],
      activeAccountId: "1",
    });
  });

  it("isolates state between store instances", () => {
    const firstStore = createUserSessionStore();
    const secondStore = createUserSessionStore();

    firstStore.getState().upsertSession({ accountId: "1", token: "token-1" });

    expect(secondStore.getState()).toMatchObject({ sessions: [], activeAccountId: null });
  });
});
