"use strict";
import React from "react";
import axios from "axios";
import update from "immutability-helper";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Checkbox from "@mui/material/Checkbox";
import Collapse from "@mui/material/Collapse";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faChevronDown,
  faChevronUp,
  faFolder,
  faBook,
} from "@fortawesome/free-solid-svg-icons";
library.add(faChevronDown, faChevronUp, faFolder, faBook);

import Subfolder from "./Folder";
import File from "./File";

class Folder extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      subfiles: [],
      open: false,
      selectedFiles: [],
    };
  }

  componentDidMount = () => {
    this.getSubfiles();
  };

  componentDidUpdate(oldProps) {
    const newProps = this.props;

    if (!oldProps.selected && newProps.selected) {
      this.setState({
        selectedFiles: this.state.subfiles.map((file) => file.globalId),
      });
    } else if (
      oldProps.parentSelected &&
      !newProps.parentSelected &&
      oldProps.selected &&
      !newProps.selected
    ) {
      this.setState({ selectedFiles: [] });
      this.props.onSelectChange(this.props.folder.globalId, false);
    }
  }

  getSubfiles = () => {
    axios
      .get("/fileTree/ajax/files", {
        params: { dir: this.props.folder.id },
      })
      .then((response) => {
        let selectedFiles = this.props.selected
          ? response.data.ajaxReturnObject.data.map((f) => f.globalId)
          : response.data.ajaxReturnObject.data
              .map((f) => f.globalId)
              .filter((id) => this.props.globalSelect.includes(id));
        this.setState({
          subfiles: response.data.ajaxReturnObject.data,
          selectedFiles: selectedFiles,
        });
      });
  };

  openFolder = (event) => {
    if (event.target.tagName.toUpperCase() != "INPUT") {
      this.setState({ open: !this.state.open });
    }
  };

  updateSelfSelect = () => {
    if (!this.props.selected && this.state.subfiles.length > 0) {
      this.setState({
        selectedFiles: this.state.subfiles.map((file) => file.globalId),
      });
      this.props.updateSelected(
        [this.props.folder.globalId],
        this.state.subfiles.map((file) => file.globalId)
      );
    } else if (this.props.selected && this.state.selectedFiles.length > 0) {
      this.setState({ selectedFiles: [], open: false });
      this.props.updateSelected(
        [],
        [this.props.folder.globalId].concat(
          this.state.subfiles.map((file) => file.globalId)
        )
      );
    } else if (this.props.level == 1 && !this.state.subfiles.length) {
      if (this.props.selected)
        this.props.updateSelected([], [this.props.folder.globalId]);
      else this.props.updateSelected([this.props.folder.globalId], []);
    }

    this.props.onSelectChange(this.props.folder.globalId, !this.props.selected);
  };

  updateSubfilesSelect = (file_id, selected) => {
    if (selected && this.state.selectedFiles.indexOf(file_id) == -1) {
      this.setState(
        {
          selectedFiles: update(this.state.selectedFiles, {
            $push: [file_id],
          }),
        },
        () => {
          if (this.state.subfiles.length == this.state.selectedFiles.length) {
            this.props.onSelectChange(this.props.folder.globalId, true);
            this.props.updateSelected(
              [this.props.folder.globalId],
              this.state.subfiles.map((file) => file.globalId)
            );
          } else {
            this.props.updateSelected(
              this.state.selectedFiles,
              this.state.subfiles.map((file) => file.globalId)
            );
          }
        }
      );
    } else if (!selected) {
      this.setState(
        {
          selectedFiles: update(this.state.selectedFiles, {
            $splice: [[this.state.selectedFiles.indexOf(file_id), 1]],
          }),
        },
        () => {
          if (this.props.selected) {
            this.props.onSelectChange(this.props.folder.globalId, false);
            this.props.updateSelected(this.state.selectedFiles, [
              this.props.folder.globalId,
            ]);
          } else {
            this.props.updateSelected(
              this.state.selectedFiles,
              this.state.subfiles.map((file) => file.globalId)
            );
          }
        }
      );
    }
  };

  renderFile = (file) => {
    if (file.folder) {
      return (
        <Subfolder
          key={file.globalId}
          onSelectChange={this.updateSubfilesSelect}
          updateSelected={this.props.updateSelected}
          folder={file}
          level={this.props.level + 1}
          selected={this.state.selectedFiles.includes(file.globalId)}
          parentSelected={this.props.selected}
          parentOpen={this.state.open}
          globalSelect={this.props.globalSelect}
        />
      );
    } else {
      return (
        <File
          key={file.globalId}
          onSelectChange={this.updateSubfilesSelect}
          updateSelected={this.props.updateSelected}
          file={file}
          level={this.props.level + 1}
          selected={this.state.selectedFiles.includes(file.globalId)}
          parentSelected={this.props.selected}
        />
      );
    }
  };

  render() {
    return (
      <>
        <ListItem
          button
          onClick={this.openFolder}
          data-test-id={`open-folder-${this.props.folder.globalId}`}
        >
          <ListItemIcon>
            <>
              <Checkbox
                indeterminate={
                  this.state.selectedFiles.length > 0 && !this.props.selected
                }
                color="primary"
                edge="start"
                checked={this.props.selected}
                onChange={this.updateSelfSelect}
                style={{ padding: 0 }}
                data-test-id={`select-folder-${this.props.folder.globalId}`}
              />
              <FontAwesomeIcon
                icon={this.props.folder.notebook ? "book" : "folder"}
                size="2x"
              />
            </>
          </ListItemIcon>
          <ListItemText primary={this.props.folder.name} />
          {this.state.subfiles.length > 0 && (
            <FontAwesomeIcon
              icon={this.state.open ? "chevron-up" : "chevron-down"}
            />
          )}
        </ListItem>
        {this.state.subfiles.length > 0 && (
          <Collapse in={this.state.open} timeout="auto" unmountOnExit>
            <List
              disablePadding
              component="div"
              style={{ paddingLeft: 15 * this.props.level }}
            >
              {this.state.subfiles.map((subfile) => this.renderFile(subfile))}
            </List>
          </Collapse>
        )}
      </>
    );
  }
}

export default Folder;
