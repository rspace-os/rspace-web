import { faFile } from "@fortawesome/free-solid-svg-icons/faFile";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Checkbox from "@mui/material/Checkbox";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import React from "react";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class Folder extends React.Component<any, any> {
  // biome-ignore lint/complexity/noUselessConstructor: initial biome migration
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
  }

  updateSelfSelect = () => {
    // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
    if (this.props.level == 1) {
      if (this.props.selected) {
        this.props.updateSelected([], [this.props.file.globalId]);
      } else {
        this.props.updateSelected([this.props.file.globalId], []);
      }
    }
    this.props.onSelectChange(this.props.file.globalId, !this.props.selected);
  };

  render() {
    return (
      // @ts-expect-error pragmatic jsx->tsx conversion: `button` prop removed from ListItem in MUI v9
      <ListItem button>
        <ListItemIcon>
          {/** biome-ignore lint/complexity/noUselessFragments: initial biome migration */}
          <>
            <Checkbox
              data-test-id={`record-select-checkbox-${this.props.file.globalId}`}
              color="primary"
              edge="start"
              checked={this.props.selected}
              onChange={this.updateSelfSelect}
              sx={{ padding: 0 }}
            />
            <FontAwesomeIcon icon={faFile} size="2x" />
          </>
        </ListItemIcon>
        <ListItemText primary={this.props.file.name} />
      </ListItem>
    );
  }
}

export default Folder;
