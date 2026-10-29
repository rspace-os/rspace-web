import * as React from "react";
import { useStore } from "zustand";
import { devtools } from "zustand/middleware";
import { createStore, type StoreApi } from "zustand/vanilla";

export type UserSession = {
  accountId: string;
};

// Client-side account selection only. Tokens and profiles belong to React Query.
type UserSessionStore = {
  sessions: UserSession[];
  activeAccountId: string | null;
  /** Also makes the account active. */
  upsertSession: (session: UserSession) => void;
  /** No-op unless a session exists for the account. */
  switchAccount: (accountId: string) => void;
  /** The next remaining session, if any, becomes active. */
  removeSession: (accountId: string) => void;
  clearSessions: () => void;
};

export function createUserSessionStore() {
  return createStore<UserSessionStore>()(
    devtools(
      (set) => ({
        sessions: [],
        activeAccountId: null,
        upsertSession: (session) =>
          set(
            (state) => ({
              sessions: [...state.sessions.filter((s) => s.accountId !== session.accountId), session],
              activeAccountId: session.accountId,
            }),
            undefined,
            "upsertSession",
          ),
        switchAccount: (accountId) =>
          set(
            (state) => (state.sessions.some((s) => s.accountId === accountId) ? { activeAccountId: accountId } : state),
            undefined,
            "switchAccount",
          ),
        removeSession: (accountId) =>
          set(
            (state) => {
              const sessions = state.sessions.filter((s) => s.accountId !== accountId);
              return {
                sessions,
                activeAccountId:
                  state.activeAccountId === accountId ? (sessions[0]?.accountId ?? null) : state.activeAccountId,
              };
            },
            undefined,
            "removeSession",
          ),
        clearSessions: () => set({ sessions: [], activeAccountId: null }, undefined, "clearSessions"),
      }),
      { name: "userSessionStore", enabled: import.meta.env.DEV },
    ),
  );
}

const UserSessionStoreContext = React.createContext<StoreApi<UserSessionStore> | null>(null);

export function UserSessionStoreProvider({
  children,
  store,
}: {
  children: React.ReactNode;
  /** Test seam: inject a pre-seeded store instance. */
  store?: StoreApi<UserSessionStore>;
}) {
  const ref = React.useRef<StoreApi<UserSessionStore> | null>(null);
  ref.current ??= store ?? createUserSessionStore();

  return <UserSessionStoreContext.Provider value={ref.current}>{children}</UserSessionStoreContext.Provider>;
}

export function useUserSessionStore<T>(selector: (state: UserSessionStore) => T) {
  const store = React.useContext(UserSessionStoreContext);
  if (!store) throw new Error("useUserSessionStore must be used within UserSessionStoreProvider");
  return useStore(store, selector);
}

export function useActiveSession() {
  return useUserSessionStore(
    (state) => state.sessions.find((session) => session.accountId === state.activeAccountId) ?? null,
  );
}
