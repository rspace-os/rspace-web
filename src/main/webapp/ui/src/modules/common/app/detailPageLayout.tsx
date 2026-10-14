import type { ReactNode } from "react";
export const detailPageClassName = "@container mx-auto max-w-5xl space-y-6 p-4 sm:p-8";
export const detailColumnsClassName = "grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_16rem]";
export function DetailPageShell({ children, busy = false }: { children: ReactNode; busy?: boolean }) {
  return (
    <main className={detailPageClassName} aria-busy={busy || undefined}>
      {children}
    </main>
  );
}
