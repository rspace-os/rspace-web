import * as React from "react";
import { useStore } from "zustand";
import { devtools } from "zustand/middleware";
import { createStore, type StoreApi } from "zustand/vanilla";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import type { BookingNoticeHost } from "@/modules/booking/feedback/BookingNotices";

export type BookingCreationContext = Readonly<{
  ownerId: string;
  triggerId: string;
  eventKind: "BOOKING" | "MAINTENANCE";
  /** Page that opened compact creation; its host owns the completion notice. */
  originHost?: BookingNoticeHost;
  target?: Readonly<BookableItemOption>;
  window?: Readonly<BookingWindowDraft>;
  initialDate?: string;
  lockTarget?: boolean;
  timelineAdjustable?: boolean;
}>;

export type BookingCreationStore = {
  activeCreation: BookingCreationContext | null;
  beginCreation: (context: BookingCreationContext) => boolean;
  /** End the active creation owned by `ownerId`; an optional context guards against stale sessions. */
  endCreation: (context: BookingCreationContext) => boolean;
};

export function createBookingCreationStore() {
  return createStore<BookingCreationStore>()(
    devtools(
      (set, get) => ({
        activeCreation: null,
        beginCreation: (context) => {
          if (get().activeCreation !== null) return false;
          set({ activeCreation: context }, undefined, "beginCreation");
          return true;
        },
        endCreation: (context) => {
          if (get().activeCreation !== context) return false;
          set({ activeCreation: null }, undefined, "endCreation");
          return true;
        },
      }),
      { name: "bookingCreationStore", enabled: import.meta.env.DEV },
    ),
  );
}

const BookingCreationStoreContext = React.createContext<StoreApi<BookingCreationStore> | null>(null);

export function BookingCreationStoreProvider({
  children,
  store,
}: {
  children: React.ReactNode;
  store?: StoreApi<BookingCreationStore>;
}) {
  const storeRef = React.useRef<StoreApi<BookingCreationStore> | null>(null);
  storeRef.current ??= store ?? createBookingCreationStore();
  return (
    <BookingCreationStoreContext.Provider value={storeRef.current}>{children}</BookingCreationStoreContext.Provider>
  );
}

export function useBookingCreationStore<T>(selector: (state: BookingCreationStore) => T) {
  const store = React.useContext(BookingCreationStoreContext);
  if (!store) throw new Error("useBookingCreationStore must be used within BookingCreationStoreProvider");
  return useStore(store, selector);
}
