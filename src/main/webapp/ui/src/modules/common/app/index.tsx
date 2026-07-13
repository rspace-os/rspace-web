import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import * as React from "react";
import { createRoot } from "react-dom/client";
import "@/modules/common/styles/index.css";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { baseUiRichTextComponents } from "@/modules/common/i18n/TransRichText";
import { UIStoreProvider } from "@/modules/common/stores/uiStore";
import { UserSessionStoreProvider } from "@/modules/common/stores/userSessionStore";
import { TooltipProvider } from "@/modules/common/ui/tooltip";
import { router } from "./router";

const queryClient = new QueryClient();

const appElement = document.getElementById("app");
if (appElement) {
  createRoot(appElement).render(
    <React.StrictMode>
      <UIStoreProvider>
        <UserSessionStoreProvider>
          <QueryClientProvider client={queryClient}>
            <I18nRoot namespaces={["common"]} componentMap={baseUiRichTextComponents}>
              <TooltipProvider>
                <RouterProvider router={router} />
              </TooltipProvider>
            </I18nRoot>
          </QueryClientProvider>
        </UserSessionStoreProvider>
      </UIStoreProvider>
    </React.StrictMode>,
  );
}
