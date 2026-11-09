import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { Suspense } from "react";
import BookingPreferencesPage from "../BookingPreferencesPage";

export function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rendered = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <BookingPreferencesPage />
      </Suspense>
    </QueryClientProvider>,
  );
  return { ...rendered, queryClient };
}
