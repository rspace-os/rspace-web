"use strict";
import React from "react";
import update from "immutability-helper";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import styled from "@emotion/styled";
import config from "./config.json";
import {
  arraysEqual,
  humanize,
  rev_humanize,
  isShortcutSingle,
  isShiftwithsomeKey,
  isShortcutForbidden,
} from "../../util/shortcuts";
import ActionsTab from "./actionsTab";
import SymbolsTab from "./symbolsTab";
import { Alert, AlertTitle } from "@mui/material";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faInfo,
  faChevronUp,
  faChevronDown,
} from "@fortawesome/free-solid-svg-icons";
import { createRoot } from "react-dom/client";
library.add(faInfo, faChevronUp, faChevronDown);

const used = config.used.split(" ");
const forbidden = config.forbidden.split(" ");

const AlertWrapper = styled.div`
  width: 100%;
  margin: 10px 0px;

  .MuiAlert-root,
  .MuiAlert-message {
    width: 100%;
  }

  .MuiAlertTitle-root {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
`;

class Shortcuts extends React.Component {
  constructor() {
    super();
    this.state = {
      instructions: false,
      tab: 0,
      actionShortcuts: {},
      symbolShortcuts: {},
      selectedKey: null,
      hasError: false,
      errorMessage: "",
    };
  }

  componentDidMount = () => {
    this.applyCurrentConfigActions();
    this.applyCurrentConfigSymbols();

    // handle reset
    parent.document.addEventListener(
      "shortcuts-reset",
      this.applyCurrentConfig
    );

    // handle reset to default
    parent.document.addEventListener(
      "shortcuts-resetDefault",
      this.applyDefaultConfig
    );

    // handle submit
    parent.document.addEventListener("shortcuts-submit", this.saveSettings);
  };

  handleChange = (event, newValue) => {
    this.setState((oldState) => {
      return {
        ...oldState,
        tab: newValue,
      };
    });
  };

  applyCurrentConfig = () => {
    if (this.state.tab == 0) {
      this.applyCurrentConfigActions();
    } else {
      this.applyCurrentConfigSymbols();
    }
  };

  applyCurrentConfigActions = () => {
    let saved_config_actions = JSON.parse(
      localStorage.getItem("custom_shortcuts_actions")
    );

    // backward compatibility
    if (!saved_config_actions) {
      saved_config_actions = JSON.parse(
        localStorage.getItem("custom_shortcuts")
      );
    }

    this.setState((oldState) => {
      return {
        ...oldState,
        actionShortcuts:
          saved_config_actions && Object.keys(saved_config_actions).length
            ? { ...saved_config_actions }
            : { ...config.default_actions },
        hasError: false,
      };
    });
  };

  applyCurrentConfigSymbols = () => {
    let saved_config_symbols = JSON.parse(
      localStorage.getItem("custom_shortcuts_symbols")
    );

    this.setState((oldState) => {
      return {
        ...oldState,
        symbolShortcuts:
          saved_config_symbols && Object.keys(saved_config_symbols).length
            ? { ...saved_config_symbols }
            : { ...config.default_symbols },
        hasError: false,
      };
    });
  };

  applyDefaultConfig = () => {
    if (this.state.tab == 0) {
      this.setState((oldState) => {
        return {
          ...oldState,
          actionShortcuts: { ...config.default_actions },
          hasError: false,
        };
      });
    } else {
      this.setState((oldState) => {
        return {
          ...oldState,
          symbolShortcuts: { ...config.default_symbols },
          hasError: false,
        };
      });
    }
  };

  saveSettings = () => {
    let new_config = {},
      shortcut;

    Object.keys(this.state.actionShortcuts).map((k) => {
      new_config[k] = rev_humanize(this.state.actionShortcuts[k]);
    });
    localStorage.setItem(
      "custom_shortcuts_actions",
      JSON.stringify(new_config)
    );

    new_config = {};

    Object.keys(this.state.symbolShortcuts).map((k) => {
      shortcut = rev_humanize(this.state.symbolShortcuts[k]);
      if (shortcut != "") {
        //don't save empty shortcut
        new_config[k] = shortcut;
      }
    });
    localStorage.setItem(
      "custom_shortcuts_symbols",
      JSON.stringify(new_config)
    );
  };

  detectShortcut = (key, e) => {
    e.stopPropagation();
    e.preventDefault();
    this.setState((oldState) => {
      return {
        ...oldState,
        selectedKey: key,
      };
    });

    let combination =
      (e.ctrlKey ? "Ctrl " : "") +
      (e.shiftKey ? "Shift " : "") +
      (e.altKey ? "Alt " : "") +
      (e.metaKey ? "Meta " : "") +
      String.fromCharCode(e.which);

    if (combination.split(" ").length >= 2 && e.which != 9) {
      // escape tab
      this.setShortcut(key, combination);
    }
  };

  setShortcut = (key, combination) => {
    let isReserved = this.isShortcutReserved(combination);
    let isForbidden = isShortcutForbidden(combination, forbidden);
    let isSingle = isShortcutSingle(combination);
    let isTwowithShift = isShiftwithsomeKey(combination);

    let errorMessage = "";
    let actionShortcuts = this.state.actionShortcuts;
    let symbolShortcuts = this.state.symbolShortcuts;

    this.setState((oldState) => {
      return {
        ...oldState,
        hasError: true,
      };
    });

    if (isReserved) {
      errorMessage = `${humanize(combination)} is being used.`;
    } else if (isForbidden) {
      errorMessage = `${humanize(
        combination
      )} is not allowed. Try another one.`;
    } else if (isSingle) {
      errorMessage = `${humanize(
        combination.split(" ")[0]
      )} alone is not allowed. Try another one.`;
    } else if (isTwowithShift) {
      errorMessage = `Shift+Somekey is not allowed. Try another one.`;
    } else {
      combination = humanize(combination.split(" ").join("+"));

      this.setState((oldState) => {
        return {
          ...oldState,
          hasError: false,
          symbolShortcuts:
            this.state.tab == 1
              ? update(symbolShortcuts, {
                  [key]: { $set: combination },
                })
              : oldState.symbolShortcuts,
          actionShortcuts:
            this.state.tab == 0
              ? update(actionShortcuts, {
                  [key]: { $set: combination },
                })
              : oldState.actionShortcuts,
        };
      });
    }

    this.setState((oldState) => {
      return {
        ...oldState,
        errorMessage: errorMessage,
      };
    });
  };

  isShortcutReserved = (combination) => {
    combination = humanize(combination).split(" ");
    let isReserved = false;

    used.forEach(function (command) {
      if (arraysEqual(command.split("+"), combination)) {
        isReserved = true;
        return false;
      }
    });
    if (isReserved) return true;

    let all_shortcuts = {
      ...this.state.actionShortcuts,
      ...this.state.symbolShortcuts,
    };
    Object.keys(all_shortcuts).map((key) => {
      if (arraysEqual(humanize(all_shortcuts[key]).split("+"), combination)) {
        isReserved = true;
      }
    });
    return isReserved;
  };

  addShortcut = (symbol) => {
    this.setState((oldState) => {
      return {
        ...oldState,
        symbolShortcuts: {
          ...oldState.symbolShortcuts,
          [symbol[0]]: "",
        },
      };
    });
  };

  resetInput = () => {
    if (this.state.hasError) {
      let actionShortcuts = this.state.actionShortcuts;
      let symbolShortcuts = this.state.symbolShortcuts;

      this.setState((oldState) => {
        return {
          ...oldState,
          symbolShortcuts:
            this.state.tab == 1
              ? update(symbolShortcuts, {
                  [this.state.selectedKey]: { $set: "" },
                })
              : oldState.symbolShortcuts,
          actionShortcuts:
            this.state.tab == 0
              ? update(actionShortcuts, {
                  [this.state.selectedKey]: { $set: "" },
                })
              : oldState.actionShortcuts,
        };
      });
    }
  };

  toggleInstructions = () => {
    this.setState((oldState) => {
      return {
        ...oldState,
        instructions: !oldState.instructions,
      };
    });
  };

  render() {
    return (
      <Grid container className="shortcut-inputs">
        <AlertWrapper>
          <Alert
            icon={
              <FontAwesomeIcon
                icon="info"
                size="2x"
                style={{ height: "30px" }}
              />
            }
            variant="outlined"
            severity="info"
          >
            <AlertTitle>
              Instructions
              <Tooltip
                title="Toggle instructions"
                aria-label="Toggle instructions"
              >
                <IconButton onClick={() => this.toggleInstructions()}>
                  {this.state.instructions && (
                    <FontAwesomeIcon icon="chevron-up" />
                  )}
                  {!this.state.instructions && (
                    <FontAwesomeIcon icon="chevron-down" />
                  )}
                </IconButton>
              </Tooltip>
            </AlertTitle>
            {this.state.tab == 0 && this.state.instructions && (
              <ul>
                <li>
                  Click on the input field next to the command you would like to
                  configure.
                </li>
                <li>
                  On your keyboard, press the key combination you would like to
                  use for that command.
                </li>
                <li>Click on 'Save'.</li>
              </ul>
            )}
            {this.state.tab == 1 && this.state.instructions && (
              <ul>
                <li>
                  All configurable symbols are divided into sections. Click on a
                  section to find the desired symbol. For example, Greek
                  characters are in the 'Extended Latin' section.
                </li>
                <li>Click on the symbol to configure its shortcut.</li>
                <li>
                  Click on the input field next to the symbol you would like to
                  configure.
                </li>
                <li>
                  On your keyboard, press the key combination you would like to
                  use for that symbol.
                </li>
                <li>Click on 'Save'.</li>
              </ul>
            )}
            {this.state.instructions && (
              <p>
                Here's{" "}
                <a
                  target="_blank"
                  href="https://www.tinymce.com/docs/advanced/keyboard-shortcuts/"
                  rel="noreferrer"
                >
                  a list
                </a>{" "}
                of reserved shortcuts already used by the editor. Please note
                that single keys, or Shift + single key, are not accepted, as
                these shortcuts will interfere with editing. Good choices for
                shortcuts are Ctrl + Shift + number/letter, or Alt + Shift +
                number/letter.
              </p>
            )}
          </Alert>
        </AlertWrapper>
        <Grid item xs={12}>
          <Tabs
            value={this.state.tab}
            onChange={this.handleChange}
            indicatorColor="primary"
            textColor="primary"
            style={{ marginBottom: "15px" }}
            centered
          >
            <Tab label="Actions" />
            <Tab label="Symbols" />
          </Tabs>
        </Grid>
        {this.state.tab == 0 && (
          <ActionsTab
            actionShortcuts={this.state.actionShortcuts}
            config={config}
            hasError={this.state.hasError}
            selectedKey={this.state.selectedKey}
            errorMessage={this.state.errorMessage}
            detectShortcut={this.detectShortcut}
            onKeyUp={this.resetInput}
          />
        )}
        {this.state.tab == 1 && (
          <SymbolsTab
            config={config}
            symbolShortcuts={this.state.symbolShortcuts}
            hasError={this.state.hasError}
            selectedKey={this.state.selectedKey}
            errorMessage={this.state.errorMessage}
            detectShortcut={this.detectShortcut}
            onNewShortcut={this.addShortcut}
            onKeyUp={this.resetInput}
          />
        )}
      </Grid>
    );
  }
}

document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-shortcuts");
  const root = createRoot(domContainer);
  root.render(<Shortcuts />);
});
