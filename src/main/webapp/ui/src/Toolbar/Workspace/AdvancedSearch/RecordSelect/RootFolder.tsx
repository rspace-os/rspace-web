import List from "@mui/material/List";
import { produce } from "immer";
import React from "react";
import axios from "@/common/axios";
import File from "./File";
import Folder from "./Folder";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class RootFolder extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
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
        const selectedFiles = response.data.ajaxReturnObject.data
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          .map((f: any) => f.globalId)
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          .filter((id: any) => this.state.globalSelect.includes(id));
        this.setState({
          subfiles: response.data.ajaxReturnObject.data,
          selectedFiles,
        });
      });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  updateSelected = (add: any, remove: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    let remaining = this.state.globalSelect.filter((f: any) => !remove.includes(f));
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    remaining = remaining.concat(add.filter((f: any) => !remaining.includes(f)));
    this.setState({ globalSelect: remaining });
    this.props.updateSelectedFiles(remaining);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  updateSubfilesSelect = (file_id: any, selected: any) => {
    if (selected && this.state.selectedFiles.indexOf(file_id) === -1) {
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      this.setState((prevState: any) => ({
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        selectedFiles: produce(prevState.selectedFiles, (draft: any) => {
          draft.push(file_id);
        }),
      }));
    } else if (!selected) {
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      this.setState((prevState: any) => ({
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        selectedFiles: produce(prevState.selectedFiles, (draft: any) => {
          const idx = draft.indexOf(file_id);
          if (idx !== -1) {
            draft.splice(idx, 1);
          }
        }),
      }));
    }
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  renderFile = (file: any) => {
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
    }
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
  };

  render() {
    return (
      <>
        {this.state.subfiles.length > 0 && (
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          <List component="nav">{this.state.subfiles.map((subfile: any) => this.renderFile(subfile))}</List>
        )}
      </>
    );
  }
}

export default RootFolder;
