import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import * as React from "react";
import { createRoot } from "react-dom/client";
import { createApiV2MutationCache } from "@/modules/common/queries/apiV2Mutation";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import "@/modules/common/styles/index.css";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { baseUiRichTextComponents } from "@/modules/common/i18n/TransRichText";
import { UIStoreProvider } from "@/modules/common/stores/uiStore";
import { TooltipProvider } from "@/modules/common/ui/tooltip";
import { createAppRouter } from "./router";

const queryClient: QueryClient = new QueryClient({
  defaultOptions: { queries: { retry: retryUnlessClientError, staleTime: 30_000 } },
  mutationCache: createApiV2MutationCache(() => queryClient),
});
const router = createAppRouter(queryClient);

const appElement = document.getElementById("app");
if (appElement) {
  createRoot(appElement).render(
    <React.StrictMode>
      <UIStoreProvider>
        <QueryClientProvider client={queryClient}>
          <I18nRoot namespaces={["common"]} componentMap={baseUiRichTextComponents}>
            <TooltipProvider>
              <RouterProvider router={router} />
            </TooltipProvider>
          </I18nRoot>
        </QueryClientProvider>
      </UIStoreProvider>
    </React.StrictMode>,
  );
}
