import { faFile } from "@fortawesome/free-solid-svg-icons/faFile";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Checkbox from "@mui/material/Checkbox";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import React from "react";

class Folder extends React.Component {
    updateSelfSelect = () => {
        if (this.props.level === 1) {
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
            <ListItem button>
                <ListItemIcon>
                    <Checkbox
                        data-test-id={`record-select-checkbox-${this.props.file.globalId}`}
                        color="primary"
                        edge="start"
                        checked={this.props.selected}
                        onChange={this.updateSelfSelect}
                        style={{ padding: 0 }}
                    />
                    <FontAwesomeIcon icon={faFile} size="2x" />
                </ListItemIcon>
                <ListItemText primary={this.props.file.name} />
            </ListItem>
        );
    }
}

export default Folder;
