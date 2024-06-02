//@flow

import React, { useEffect, type Node } from "react";
import MaterialsDialog from "./MaterialsDialog";
import { useParams } from "react-router-dom";
import useStores from "../../stores/use-stores";
import AlwaysNewWindowNavigationContext from "../../components/AlwaysNewWindowNavigationContext";

export default function StandaloneListOfMaterialsPage(): Node {
  const { lomId } = useParams();
  const { materialsStore } = useStores();

  useEffect(() => {
    void materialsStore.getMaterialsListing(parseInt(lomId, 10));
  }, [lomId]);

  useEffect(() => {
    window.addEventListener("beforeunload", () => {
      window.opener.postMessage("closing", window.origin);
    });
  }, []);

  return (
    <AlwaysNewWindowNavigationContext>
      <MaterialsDialog
        open={true}
        standalonePage
        setOpen={(open) => {
          if (!open) window.close();
        }}
      />
    </AlwaysNewWindowNavigationContext>
  );
}
