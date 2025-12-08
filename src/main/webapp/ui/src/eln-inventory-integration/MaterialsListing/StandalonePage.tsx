import type React from "react";
import { useEffect } from "react";
import { useParams } from "react-router-dom";
import AlwaysNewWindowNavigationContext from "../../components/AlwaysNewWindowNavigationContext";
import useStores from "../../stores/use-stores";
import MaterialsDialog from "./MaterialsDialog";

export default function StandaloneListOfMaterialsPage(): React.ReactNode {
    const { lomId } = useParams();
    const { materialsStore } = useStores();

    useEffect(() => {
        if (lomId === undefined) throw new Error("lomId is undefined");
        void materialsStore.getMaterialsListing(parseInt(lomId, 10));
    }, [lomId, materialsStore.getMaterialsListing]);

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
