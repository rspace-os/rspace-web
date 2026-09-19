import type React from "react";
import { useEffect, useState } from "react";
import { useParams } from "react-router";
import AlwaysNewWindowNavigationContext from "../../components/AlwaysNewWindowNavigationContext";
import InventoryAnalytics from "../../Inventory/Analytics";
import useStores from "../../stores/use-stores";
import MaterialsDialog from "./MaterialsDialog";

export default function StandaloneListOfMaterialsPage(): React.ReactNode {
  const { lomId } = useParams();
  const { materialsStore } = useStores();
  const [dialogOpen, setDialogOpen] = useState(false);

  useEffect(() => setDialogOpen(true), []);

  useEffect(() => {
    if (lomId === undefined) throw new Error("lomId is undefined");
    void materialsStore.getMaterialsListing(parseInt(lomId, 10));
  }, [lomId]);

  useEffect(() => {
    window.addEventListener("beforeunload", () => {
      window.opener.postMessage("closing", window.origin);
    });
  }, []);

  return (
    <AlwaysNewWindowNavigationContext>
      <InventoryAnalytics>
        <MaterialsDialog
          open={dialogOpen}
          standalonePage
          setOpen={(open) => {
            if (!open) window.close();
          }}
        />
      </InventoryAnalytics>
    </AlwaysNewWindowNavigationContext>
  );
}
