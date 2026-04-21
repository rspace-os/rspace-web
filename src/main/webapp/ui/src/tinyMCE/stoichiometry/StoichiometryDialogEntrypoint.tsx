import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DialogBoundary } from "@/components/DialogBoundary";
import ConfirmProvider from "../../components/ConfirmProvider";
import StoichiometryDialog, {
  type StandaloneDialogInnerProps,
} from "./dialog/StoichiometryDialog";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
});

const StoichiometryDialogEntrypoint = (props: StandaloneDialogInnerProps) => {
  return (
    <QueryClientProvider client={queryClient}>
      <DialogBoundary>
        <ConfirmProvider>
          <StoichiometryDialog {...props} />
        </ConfirmProvider>
      </DialogBoundary>
    </QueryClientProvider>
  );
};

export default StoichiometryDialogEntrypoint;
