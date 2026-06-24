import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import AddTag from "../../../components/Tags/AddTag";
import TagListing from "../../../components/Tags/TagListing";
import NavigateContext from "../../../stores/contexts/Navigate";
import type { HasEditableFields } from "../../../stores/definitions/Editable";
import type { Tag } from "../../../stores/definitions/Tag";
import BatchFormField from "../Inputs/BatchFormField";

const MAX_TOTAL = 8000;
const MIN_EACH = 2;

function Tags<
  Fields extends {
    tags: Array<Tag>;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({ fieldOwner }: { fieldOwner: FieldOwner }): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const errorMessage = () => {
    if (fieldOwner.fieldValues.tags.map((t) => t.value).join(",").length > MAX_TOTAL)
      return t("fields.tags.validation.maxLength", { max: MAX_TOTAL });
    if (fieldOwner.fieldValues.tags.some((t) => t.value.length < MIN_EACH))
      return t("fields.tags.validation.minLength", { min: MIN_EACH });
    return null;
  };

  return (
    <BatchFormField
      label={t("fields.tags.label")}
      /*
       * One of the reasons for converting the list of tags into a string is
       * that if the list is empty then `!value` is true, and so `noValueLabel`
       * is used. If we left it as an array then `!value` would always be
       * false. Additionally `maxLength` now takes into account the commas.
       */
      value={fieldOwner.fieldValues.tags.map((t) => t.value).join(",")}
      error={Boolean(errorMessage())}
      disabled={!fieldOwner.isFieldEditable("tags")}
      maxLength={MAX_TOTAL}
      helperText={errorMessage()}
      noValueLabel={fieldOwner.noValueLabel.tags ?? t("fields.tags.none")}
      canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
      setDisabled={(d) => {
        fieldOwner.setFieldEditable("tags", d);
      }}
      renderInput={({ disabled }) => (
        // id prop is ignored because there is no singular HTMLInputElement to attach it to
        <TagListing
          onClick={(tag) => {
            navigate(`/inventory/search?query=l: (tags:"${tag.value}")`);
          }}
          tags={fieldOwner.fieldValues.tags}
          {...(fieldOwner.isFieldEditable("tags")
            ? {
                onDelete: (index) => {
                  fieldOwner.setFieldsDirty({
                    tags: fieldOwner.fieldValues.tags.toSpliced(index, 1),
                  });
                },
              }
            : {})}
          endAdornment={
            !disabled && (
              <AddTag
                onSelection={(tag) => {
                  if (fieldOwner.fieldValues.tags.includes(tag as Tag)) {
                    console.warn("Preventing the same tag from being added twice");
                    return;
                  }
                  fieldOwner.setFieldsDirty({
                    tags: [...fieldOwner.fieldValues.tags, tag as Tag],
                  });
                }}
                value={fieldOwner.fieldValues.tags}
              />
            )
          }
        />
      )}
    />
  );
}

export default observer(Tags);
