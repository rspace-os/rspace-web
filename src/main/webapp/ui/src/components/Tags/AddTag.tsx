import React, { useState } from "react";
import Chip from "@mui/material/Chip";
import AddIcon from "@mui/icons-material/Add";
import TagsCombobox from "./TagsCombobox";
import RsSet from "../../util/set";
import { Optional } from "../../util/optional";

type AddTagArgs<
  Toggle extends
    | {
        enforce: true;
        tag: {
          value: string;
          vocabulary: string;
          uri: string;
          version: string;
        };
      }
    | {
        enforce: false;
        tag: {
          value: string;
          vocabulary: Optional<string>;
          uri: Optional<string>;
          version: Optional<string>;
        };
      }
> = {
  enforceOntologies?: Toggle["enforce"];
  onSelection: (tag: Toggle["tag"]) => void;
  value: Array<Toggle["tag"]>;
  disabled?: boolean;
};

/**
 * This component provides a button to add a tag to a list of tags.
 * It is parameterized by the type of tag that it will add, requiring that the
 * selected tag have all of the metadata when `enforceOntologies` is true.
 */
export default function AddTag<
  Toggle extends
    | {
        enforce: true;
        tag: {
          value: string;
          vocabulary: string;
          uri: string;
          version: string;
        };
      }
    | {
        enforce: false;
        tag: {
          value: string;
          vocabulary: Optional<string>;
          uri: Optional<string>;
          version: Optional<string>;
        };
      }
>({
  onSelection,
  value,
  enforceOntologies = false,
  disabled = false,
}: AddTagArgs<Toggle>): React.ReactNode {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  return (
    <>
      <Chip
        icon={<AddIcon />}
        color={disabled ? "default" : "primary"}
        label="Add Tag"
        onClick={
          disabled
            ? () => {}
            : (e) => {
                setAnchorEl(e.currentTarget);
              }
        }
        clickable={!disabled}
        skipFocusWhenDisabled
      />
      {/*
       * Only mount the combobox once its popover is open. TagsCombobox calls
       * MUI's useAutocomplete, which validates on mount that its input element
       * exists; mounting it while the popover is closed (e.g. as soon as the
       * sample form enters edit mode) runs that validation before the input is
       * rendered, producing a console error. Mounting it only when `anchorEl`
       * is set guarantees the input is present.
       */}
      {anchorEl && (
        <TagsCombobox
          enforceOntologies={enforceOntologies}
          onSelection={onSelection}
          value={new RsSet(value)}
          anchorEl={anchorEl}
          onClose={() => setAnchorEl(null)}
        />
      )}
    </>
  );
}
