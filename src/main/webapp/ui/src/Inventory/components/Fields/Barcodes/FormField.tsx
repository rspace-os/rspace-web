import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import docLinks from "../../../../assets/DocLinks";
import type { BarcodeRecord } from "../../../../stores/definitions/Barcode";
import type { HasEditableFields } from "../../../../stores/definitions/Editable";
import type { Factory } from "../../../../stores/definitions/Factory";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import BatchFormField from "../../Inputs/BatchFormField";
import FieldCard from "./FieldCard";

function BarcodesFromField<
    Fields extends {
        barcodes: Array<BarcodeRecord>;
    },
    FieldOwner extends HasEditableFields<Fields>,
>({
    fieldOwner,
    factory,
    connectedItem,
}: {
    fieldOwner: FieldOwner;
    connectedItem?: InventoryRecord;
    factory: Factory;
}): ReactNode {
    return (
        <BatchFormField
            /*
             * We use a truthy value because all records at least have the auto
             * generated barcode and thus this field should never show "No Value"
             */
            value={true}
            label=""
            explanation={
                fieldOwner.isFieldEditable("barcodes") ? (
                    <>
                        See the documentation for information on{" "}
                        <a href={docLinks.barcodes} target="_blank" rel="noreferrer">
                            adding barcodes
                        </a>
                        .
                    </>
                ) : null
            }
            canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
            disabled={!fieldOwner.isFieldEditable("barcodes")}
            setDisabled={(checked) => {
                fieldOwner.setFieldEditable("barcodes", checked);
            }}
            renderInput={() => <FieldCard fieldOwner={fieldOwner} factory={factory} connectedItem={connectedItem} />}
        />
    );
}

export default observer(BarcodesFromField);
