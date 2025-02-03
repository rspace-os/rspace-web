//@flow

import React, { type Node, useState } from "react";
import Chip from "@mui/material/Chip";
import AddIcon from "@mui/icons-material/Add";
import TagsCombobox from "./TagsCombobox";
import RsSet from "../../util/set";
import { Optional } from "../../util/optional";

type AddTagArgs<
  Toggle:
    | {|
        enforce: true,
        tag: {|
          value: string,
          vocabulary: string,
          uri: string,
          version: string,
        |},
      |}
    | {|
        enforce: false,
        tag: {|
          value: string,
          vocabulary: Optional<string>,
          uri: Optional<string>,
          version: Optional<string>,
        |},
      |}
> = {|
  enforceOntologies?: Toggle["enforce"],
  onSelection: (Toggle["tag"]) => void,
  value: Array<Toggle["tag"]>,
  disabled?: boolean,
|};

export default function AddTag<
  Toggle:
    | {|
        enforce: true,
        tag: {|
          value: string,
          vocabulary: string,
          uri: string,
          version: string,
        |},
      |}
    | {|
        enforce: false,
        tag: {|
          value: string,
          vocabulary: Optional<string>,
          uri: Optional<string>,
          version: Optional<string>,
        |},
      |}
>({
  onSelection,
  value,
  enforceOntologies = false,
  disabled = false,
}: AddTagArgs<Toggle>): Node {
  const [anchorEl, setAnchorEl] = useState(null);
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
      <TagsCombobox
        enforceOntologies={enforceOntologies}
        onSelection={onSelection}
        value={new RsSet(value)}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
      />
    </>
  );
}
