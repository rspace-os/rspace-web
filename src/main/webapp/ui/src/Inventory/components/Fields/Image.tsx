// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { Alert } from "@mui/material";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import ImageField from "../../../components/Inputs/ImageField";
import SearchContext from "../../../stores/contexts/Search";
import type { HasEditableFields } from "../../../stores/definitions/Editable";
import { capImageAt1MB } from "../../../util/images";
import type { BlobUrl } from "../../../util/types";
import BatchFormField from "../Inputs/BatchFormField";

const MAX = 25;

function Image<
  Fields extends {
    image: BlobUrl | null;
    newBase64Image: string | null;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({ fieldOwner, alt }: { fieldOwner: FieldOwner; alt: string }): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { search } = useContext(SearchContext);
  const isFieldEditable = fieldOwner.isFieldEditable("image");
  let tooManytoBatchThis = false;
  if (search?.batchEditingRecords) {
    tooManytoBatchThis = search.batchEditingRecords.size > MAX;
  }
  const disabled = !isFieldEditable;
  const itemImage = fieldOwner.fieldValues.image;
  return (
    <BatchFormField
      value={itemImage}
      label="Preview Image"
      disabled={!fieldOwner.isFieldEditable("image")}
      explanation={itemImage ? t("fields.image.explanation") : null}
      canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit && !tooManytoBatchThis}
      setDisabled={(checked) => {
        fieldOwner.setFieldEditable("image", checked);
      }}
      renderInput={({ value }) => (
        <>
          <ImageField
            storeImage={({ dataURL, file }) => {
              void (async () => {
                const scaledImage = await capImageAt1MB(file, dataURL);
                fieldOwner.setFieldsDirty({
                  image: scaledImage,
                  newBase64Image: scaledImage,
                });
              })();
            }}
            imageAsObjectURL={value}
            disabled={disabled}
            height={150}
            width={150}
            id="preview-image-form-element"
            noValueLabel={fieldOwner.noValueLabel.image}
            alt={alt}
          />
          {tooManytoBatchThis && <Alert severity="info">{t("fields.image.tooManyToEdit", { max: MAX })}</Alert>}
        </>
      )}
    />
  );
}

export default observer(Image);
