/** @prototype Storybook-only UI exploration; not production-ready. */
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import { useEffect, useState } from "react";
import InventoryPicker from "@/Inventory/components/Picker/Picker";
import type { RequestableSample } from "@/Inventory/Requests/types";
import AlwaysNewFactory from "@/stores/models/Factory/AlwaysNewFactory";
import Search from "@/stores/models/Search";
import getRootStore from "@/stores/stores/getRootStore";
import { mockSampleSearch } from "./InventoryChromePrototype";

/** Real Inventory picker, with Storybook-only sample search responses. */
export default function SampleFilterPickerPrototype({
  samples,
  title,
  onPick,
  onClose,
}: {
  samples: ReadonlyArray<RequestableSample>;
  title: string;
  onPick: (globalId: string) => void;
  onClose: () => void;
}) {
  const [search] = useState(
    () =>
      new Search({
        factory: new AlwaysNewFactory(),
        fetcherParams: { resultType: "SAMPLE", pageSize: 5, orderBy: "name", order: "asc" },
        uiConfig: {
          allowedSearchModules: new Set(["TYPE", "STATUS"]),
          allowedTypeFilters: new Set(["SAMPLE"]),
          adjustableColumns: ["globalId"],
          selectionMode: "SINGLE",
          instantConfirm: false,
        },
      }),
  );
  useEffect(() => {
    mockSampleSearch(samples);
    void getRootStore()
      .authStore.authenticate()
      .then(() => search.fetcher.performInitialSearch(null));
  }, [search, samples]);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md" aria-label={title}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent sx={{ height: "65vh", p: 1 }}>
        <InventoryPicker
          search={search}
          paddingless
          showActions
          onCancel={onClose}
          onAddition={([record]) => {
            if (record?.globalId) onPick(record.globalId);
          }}
        />
      </DialogContent>
    </Dialog>
  );
}
