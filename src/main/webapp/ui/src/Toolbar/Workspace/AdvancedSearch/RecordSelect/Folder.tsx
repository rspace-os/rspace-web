import { faBook } from "@fortawesome/free-solid-svg-icons/faBook";
import { faChevronDown } from "@fortawesome/free-solid-svg-icons/faChevronDown";
import { faChevronUp } from "@fortawesome/free-solid-svg-icons/faChevronUp";
import { faFolder } from "@fortawesome/free-solid-svg-icons/faFolder";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Checkbox from "@mui/material/Checkbox";
import Collapse from "@mui/material/Collapse";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import { produce } from "immer";
import React from "react";
import axios from "@/common/axios";
import File from "./File";
import Subfolder from "./Folder";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class Folder extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
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

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  componentDidUpdate(oldProps: any) {
    const newProps = this.props;

    if (!oldProps.selected && newProps.selected) {
      this.setState({
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        selectedFiles: this.state.subfiles.map((file: any) => file.globalId),
      });
    } else if (oldProps.parentSelected && !newProps.parentSelected && oldProps.selected && !newProps.selected) {
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
        const selectedFiles = this.props.selected
          ? // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
            response.data.ajaxReturnObject.data.map((f: any) => f.globalId)
          : response.data.ajaxReturnObject.data
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              .map((f: any) => f.globalId)
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              .filter((id: any) => this.props.globalSelect.includes(id));
        this.setState({
          subfiles: response.data.ajaxReturnObject.data,
          selectedFiles,
        });
      });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  openFolder = (event: any) => {
    if (event.target.tagName.toUpperCase() !== "INPUT") {
      this.setState({ open: !this.state.open });
    }
  };

  updateSelfSelect = () => {
    if (!this.props.selected && this.state.subfiles.length > 0) {
      this.setState({
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        selectedFiles: this.state.subfiles.map((file: any) => file.globalId),
      });
      this.props.updateSelected(
        [this.props.folder.globalId],
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        this.state.subfiles.map((file: any) => file.globalId),
      );
    } else if (this.props.selected && this.state.selectedFiles.length > 0) {
      this.setState({ selectedFiles: [], open: false });
      this.props.updateSelected(
        [],
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        [this.props.folder.globalId].concat(this.state.subfiles.map((file: any) => file.globalId)),
      );
    } else if (this.props.level === 1 && !this.state.subfiles.length) {
      if (this.props.selected) this.props.updateSelected([], [this.props.folder.globalId]);
      else this.props.updateSelected([this.props.folder.globalId], []);
    }

    this.props.onSelectChange(this.props.folder.globalId, !this.props.selected);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  updateSubfilesSelect = (file_id: any, selected: any) => {
    if (selected && this.state.selectedFiles.indexOf(file_id) === -1) {
      this.setState(
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        (prevState: any) => ({
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          selectedFiles: produce(prevState.selectedFiles, (draft: any) => {
            draft.push(file_id);
          }),
        }),
        () => {
          if (this.state.subfiles.length === this.state.selectedFiles.length) {
            this.props.onSelectChange(this.props.folder.globalId, true);
            this.props.updateSelected(
              [this.props.folder.globalId],
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              this.state.subfiles.map((file: any) => file.globalId),
            );
          } else {
            this.props.updateSelected(
              this.state.selectedFiles,
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              this.state.subfiles.map((file: any) => file.globalId),
            );
          }
        },
      );
    } else if (!selected) {
      this.setState(
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        (prevState: any) => ({
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          selectedFiles: produce(prevState.selectedFiles, (draft: any) => {
            const idx = draft.indexOf(file_id);
            if (idx !== -1) {
              draft.splice(idx, 1);
            }
          }),
        }),
        () => {
          if (this.props.selected) {
            this.props.onSelectChange(this.props.folder.globalId, false);
            this.props.updateSelected(this.state.selectedFiles, [this.props.folder.globalId]);
          } else {
            this.props.updateSelected(
              this.state.selectedFiles,
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              this.state.subfiles.map((file: any) => file.globalId),
            );
          }
        },
      );
    }
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  renderFile = (file: any) => {
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
    }
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
  };

  render() {
    return (
      <>
        {/* @ts-expect-error pragmatic jsx->tsx conversion: `button` prop removed from ListItem in MUI v9 */}
        <ListItem button onClick={this.openFolder} data-test-id={`open-folder-${this.props.folder.globalId}`}>
          <ListItemIcon>
            <Checkbox
              indeterminate={this.state.selectedFiles.length > 0 && !this.props.selected}
              color="primary"
              edge="start"
              checked={this.props.selected}
              onChange={this.updateSelfSelect}
              sx={{ padding: 0 }}
              data-test-id={`select-folder-${this.props.folder.globalId}`}
            />
            {this.props.folder.notebook ? (
              <FontAwesomeIcon icon={faBook} size="2x" />
            ) : (
              <FontAwesomeIcon icon={faFolder} size="2x" />
            )}
          </ListItemIcon>
          <ListItemText primary={this.props.folder.name} />
          {this.state.subfiles.length > 0 &&
            (this.state.open ? <FontAwesomeIcon icon={faChevronUp} /> : <FontAwesomeIcon icon={faChevronDown} />)}
        </ListItem>
        {this.state.subfiles.length > 0 && (
          <Collapse in={this.state.open} timeout="auto" unmountOnExit>
            <List disablePadding component="div" sx={{ paddingLeft: `${15 * this.props.level}px` }}>
              {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
              {this.state.subfiles.map((subfile: any) => this.renderFile(subfile))}
            </List>
          </Collapse>
        )}
      </>
    );
  }
}

export default Folder;
