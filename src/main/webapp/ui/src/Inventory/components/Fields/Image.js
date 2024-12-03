// @flow

import ImageField from "../../../components/Inputs/ImageField";
import React, { type Node, useContext } from "react";
import { observer } from "mobx-react-lite";
import { doNotAwait } from "../../../util/Util";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import { type BlobUrl } from "../../../stores/stores/ImageStore";
import { capImageAt1MB } from "../../../util/images";
import SearchContext from "../../../stores/contexts/Search";
import { Alert } from "@mui/material";
import BatchFormField from "../Inputs/BatchFormField";

const CANVAS_ID = "previewCanvas";
const MAX = 25;

function Image<
  Fields: {
    image: ?BlobUrl,
    newBase64Image: ?string,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({ fieldOwner, alt }: {| fieldOwner: FieldOwner, alt: string |}): Node {
  const { search } = useContext(SearchContext);
  const isFieldEditable = fieldOwner.isFieldEditable("image");
  let tooManytoBatchThis: boolean = false;
  if (search && search.batchEditingRecords) {
    tooManytoBatchThis = search.batchEditingRecords.size > MAX;
  }
  const disabled = !isFieldEditable;
  const itemImage = fieldOwner.fieldValues.image;
  return (
    <BatchFormField
      value={itemImage}
      label="Preview Image"
      disabled={!fieldOwner.isFieldEditable("image")}
      explanation={
        itemImage ? (
          <>
            Tap to view at full resolution, scroll to exit (or pinch on mobile)
          </>
        ) : null
      }
      canChooseWhichToEdit={
        fieldOwner.canChooseWhichToEdit && !tooManytoBatchThis
      }
      setDisabled={(checked) => {
        fieldOwner.setFieldEditable("image", checked);
      }}
      renderInput={({ value }) => (
        <>
          <ImageField
            storeImage={doNotAwait(async ({ dataURL, file }) => {
              const scaledImage = await capImageAt1MB(file, dataURL, CANVAS_ID);
              fieldOwner.setFieldsDirty({
                image: scaledImage,
                newBase64Image: scaledImage,
              });
            })}
            imageAsObjectURL={value}
            disabled={disabled}
            height={150}
            width={150}
            id="preview-image-form-element"
            noValueLabel={fieldOwner.noValueLabel.image}
            alt={alt}
          />
          <canvas id={CANVAS_ID} style={{ display: "none" }} />
          {tooManytoBatchThis && (
            <Alert severity="info">
              The image can only be edited when no more than 25 items are
              selected.
            </Alert>
          )}
        </>
      )}
    />
  );
}

export default (observer(Image): typeof Image);
