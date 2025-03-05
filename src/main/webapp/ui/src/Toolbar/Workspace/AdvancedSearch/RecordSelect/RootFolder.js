"use strict";
import React from "react";
import update from "immutability-helper";
import axios from "@/common/axios";
import List from "@mui/material/List";

import Folder from "./Folder";
import File from "./File";

class RootFolder extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      subfiles: [],
      selectedFiles: [],
      globalSelect: props.globalSelect,
    };
  }

  componentDidMount = () => {
    this.getSubfiles();
  };

  getSubfiles = () => {
    axios
      .get("/fileTree/ajax/files", {
        params: { dir: "/" },
      })
      .then((response) => {
        let selectedFiles = response.data.ajaxReturnObject.data
          .map((f) => f.globalId)
          .filter((id) => this.state.globalSelect.includes(id));
        this.setState({
          subfiles: response.data.ajaxReturnObject.data,
          selectedFiles: selectedFiles,
        });
      });
  };

  updateSelected = (add, remove) => {
    let remaining = this.state.globalSelect.filter((f) => !remove.includes(f));
    remaining = remaining.concat(add.filter((f) => !remaining.includes(f)));
    this.setState({ globalSelect: remaining });
    this.props.updateSelectedFiles(remaining);
  };

  updateSubfilesSelect = (file_id, selected) => {
    if (selected && this.state.selectedFiles.indexOf(file_id) == -1) {
      this.setState({
        selectedFiles: update(this.state.selectedFiles, {
          $push: [file_id],
        }),
      });
    } else if (!selected) {
      this.setState({
        selectedFiles: update(this.state.selectedFiles, {
          $splice: [[this.state.selectedFiles.indexOf(file_id), 1]],
        }),
      });
    }
  };

  renderFile = (file) => {
    if (file.folder) {
      return (
        <Folder
          key={file.globalId}
          folder={file}
          level={1}
          updateSelected={this.updateSelected}
          onSelectChange={this.updateSubfilesSelect}
          selected={this.state.selectedFiles.includes(file.globalId)}
          parentOpen={this.state.open}
          globalSelect={this.state.globalSelect}
        />
      );
    } else {
      return (
        <File
          key={file.globalId}
          file={file}
          level={1}
          updateSelected={this.updateSelected}
          onSelectChange={this.updateSubfilesSelect}
          selected={this.state.selectedFiles.includes(file.globalId)}
        />
      );
    }
  };

  render() {
    return (
      <>
        {this.state.subfiles.length > 0 && (
          <List component="nav">
            {this.state.subfiles.map((subfile) => this.renderFile(subfile))}
          </List>
        )}
      </>
    );
  }
}

export default RootFolder;
