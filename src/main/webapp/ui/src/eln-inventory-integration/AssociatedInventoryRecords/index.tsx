import React, { useState, useEffect } from "react";
import useStores from "../../stores/use-stores";
import { createRoot } from "react-dom/client";
import { type ElnDocumentId } from "../../stores/models/MaterialsModel";
import { observer } from "mobx-react-lite";
import Collapse from "@mui/material/Collapse";
import NoValue from "../../components/NoValue";
import RsSet from "../../util/set";
import { type InventoryRecord } from "../../stores/definitions/InventoryRecord";

type ListingContentsProps = {
  setOfRecords: RsSet<InventoryRecord>;
  loading: boolean;
};

function ListingContents({ setOfRecords, loading }: ListingContentsProps) {
  if (loading) return <NoValue label="Loading" />;
  if (setOfRecords.size === 0)
    return <>The document has no connected Inventory items.</>;
  return (
    <>
      {setOfRecords.map(({ name, globalId, permalinkURL }) => (
        <li key={globalId}>
          <a href={permalinkURL || ""}>{name}</a>
        </li>
      ))}
    </>
  );
}

type AssociatedInventoryRecordsArgs = {
  elnDocumentId: ElnDocumentId;
};

const AssociatedInventoryRecords = observer(
  function AssociatedInventoryRecords({
    elnDocumentId,
  }: AssociatedInventoryRecordsArgs) {
    const { materialsStore } = useStores();
    const [open, setOpen] = useState(false);

    useEffect(() => {
      if (open) {
        void materialsStore.getDocumentMaterialsListings(elnDocumentId);
      }
    }, [open, elnDocumentId, materialsStore]);

    useEffect(() => {
      const handleListOfMaterialsOpened = () => {
        /*
         * Close the listing so that the data has to be fetched in case there
         * have been changes to the inventory records included in the list of materials
         */
        setOpen(false);
      };

      window.addEventListener(
        "listOfMaterialsOpened",
        handleListOfMaterialsOpened
      );

      return () => {
        window.removeEventListener(
          "listOfMaterialsOpened",
          handleListOfMaterialsOpened
        );
      };
    }, []);

    if (isNaN(elnDocumentId)) return null;
    return (
      <>
        <button
          onClick={() => {
            setOpen(!open);
          }}
          className="btn btn-primary"
        >
          Inventory Items
        </button>
        <Collapse in={open}>
          <ul>
            <ListingContents
              setOfRecords={materialsStore.allInvRecordsFromAllDocumentLists}
              loading={materialsStore.loading}
            />
          </ul>
        </Collapse>
      </>
    );
  }
);

const wrapperDiv = document.getElementById("inventoryRecordList");
if (wrapperDiv) {
  const root = createRoot(wrapperDiv);
  root.render(
    <AssociatedInventoryRecords
      elnDocumentId={parseInt(wrapperDiv.dataset.documentid || "0", 10)}
    />
  );
}

export default AssociatedInventoryRecords;
