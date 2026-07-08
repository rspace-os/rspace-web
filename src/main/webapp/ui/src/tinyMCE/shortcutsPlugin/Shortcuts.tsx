import { faChevronDown } from "@fortawesome/free-solid-svg-icons/faChevronDown";
import { faChevronUp } from "@fortawesome/free-solid-svg-icons/faChevronUp";
import { faInfo } from "@fortawesome/free-solid-svg-icons/faInfo";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Tooltip from "@mui/material/Tooltip";
import { produce } from "immer";
import React from "react";
import { createRoot } from "react-dom/client";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import i18n from "@/modules/common/i18n";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import TransRichText from "@/modules/common/i18n/TransRichText";
import {
  arraysEqual,
  humanize,
  isShiftwithsomeKey,
  isShortcutForbidden,
  isShortcutSingle,
  rev_humanize,
} from "../../util/shortcuts";
import ActionsTab from "./ActionsTab";
import configJson from "./config.json";
import SymbolsTab from "./SymbolsTab";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const config: any = configJson;

const used = config.used.split(" ");
const forbidden = config.forbidden.split(" ");

type ShortcutMap = Record<string, string>;

type ShortcutsState = {
  instructions: boolean;
  tab: number;
  actionShortcuts: ShortcutMap;
  symbolShortcuts: ShortcutMap;
  selectedKey: string | null;
  hasError: boolean;
  errorMessage: string;
};

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class Shortcuts extends React.Component<any, ShortcutsState> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
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
    parent.document.addEventListener("shortcuts-reset", this.applyCurrentConfig);

    // handle reset to default
    parent.document.addEventListener("shortcuts-resetDefault", this.applyDefaultConfig);

    // handle submit
    parent.document.addEventListener("shortcuts-submit", this.saveSettings);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleChange = (_event: any, newValue: number) => {
    this.setState((oldState) => {
      return {
        ...oldState,
        tab: newValue,
      };
    });
  };

  applyCurrentConfig = () => {
    if (this.state.tab === 0) {
      this.applyCurrentConfigActions();
    } else {
      this.applyCurrentConfigSymbols();
    }
  };

  applyCurrentConfigActions = () => {
    let saved_config_actions = JSON.parse(localStorage.getItem("custom_shortcuts_actions") as string);

    // backward compatibility
    if (!saved_config_actions) {
      saved_config_actions = JSON.parse(localStorage.getItem("custom_shortcuts") as string);
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
    const saved_config_symbols = JSON.parse(localStorage.getItem("custom_shortcuts_symbols") as string);

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
    if (this.state.tab === 0) {
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
    let new_config: Record<string, unknown> = {},
      shortcut = "";

    // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
    Object.keys(this.state.actionShortcuts).map((k) => {
      new_config[k] = rev_humanize(this.state.actionShortcuts[k]);
    });
    localStorage.setItem("custom_shortcuts_actions", JSON.stringify(new_config));

    new_config = {};

    // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
    Object.keys(this.state.symbolShortcuts).map((k) => {
      shortcut = rev_humanize(this.state.symbolShortcuts[k]);
      if (shortcut !== "") {
        //don't save empty shortcut
        new_config[k] = shortcut;
      }
    });
    localStorage.setItem("custom_shortcuts_symbols", JSON.stringify(new_config));
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  detectShortcut = (key: string, e: any) => {
    e.stopPropagation();
    e.preventDefault();
    this.setState((oldState) => {
      return {
        ...oldState,
        selectedKey: key,
      };
    });

    const combination =
      (e.ctrlKey ? "Ctrl " : "") +
      (e.shiftKey ? "Shift " : "") +
      (e.altKey ? "Alt " : "") +
      (e.metaKey ? "Meta " : "") +
      String.fromCharCode(e.which);

    if (combination.split(" ").length >= 2 && e.which !== 9) {
      // escape tab
      this.setShortcut(key, combination);
    }
  };

  setShortcut = (key: string, combination: string) => {
    const isReserved = this.isShortcutReserved(combination);
    const isForbidden = isShortcutForbidden(combination, forbidden);
    const isSingle = isShortcutSingle(combination);
    const isTwowithShift = isShiftwithsomeKey(combination);

    let errorMessage = "";
    this.setState((oldState) => {
      return {
        ...oldState,
        hasError: true,
      };
    });

    if (isReserved) {
      errorMessage = `${humanize(combination)} is being used.`;
    } else if (isForbidden) {
      errorMessage = `${humanize(combination)} is not allowed. Try another one.`;
    } else if (isSingle) {
      errorMessage = `${humanize(combination.split(" ")[0])} alone is not allowed. Try another one.`;
    } else if (isTwowithShift) {
      errorMessage = `Shift+Somekey is not allowed. Try another one.`;
    } else {
      combination = humanize(combination.split(" ").join("+"));

      this.setState((oldState) => {
        return {
          ...oldState,
          hasError: false,
          symbolShortcuts:
            this.state.tab === 1
              ? produce(oldState.symbolShortcuts, (draft) => {
                  draft[key] = combination;
                })
              : oldState.symbolShortcuts,
          actionShortcuts:
            this.state.tab === 0
              ? produce(oldState.actionShortcuts, (draft) => {
                  draft[key] = combination;
                })
              : oldState.actionShortcuts,
        };
      });
    }

    this.setState((oldState) => {
      return {
        ...oldState,
        errorMessage,
      };
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  isShortcutReserved = (combination: any) => {
    combination = humanize(combination).split(" ");
    let isReserved = false;

    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
    used.forEach((command: any) => {
      if (arraysEqual(command.split("+"), combination)) {
        isReserved = true;
        return false;
      }
    });
    if (isReserved) return true;

    const all_shortcuts = {
      ...this.state.actionShortcuts,
      ...this.state.symbolShortcuts,
    };
    // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
    Object.keys(all_shortcuts).map((key) => {
      if (arraysEqual(humanize(all_shortcuts[key]).split("+"), combination)) {
        isReserved = true;
      }
    });
    return isReserved;
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  addShortcut = (symbol: any) => {
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
      this.setState((oldState) => {
        return {
          ...oldState,
          symbolShortcuts:
            this.state.tab === 1
              ? produce(oldState.symbolShortcuts, (draft) => {
                  draft[this.state.selectedKey as string] = "";
                })
              : oldState.symbolShortcuts,
          actionShortcuts:
            this.state.tab === 0
              ? produce(oldState.actionShortcuts, (draft) => {
                  draft[this.state.selectedKey as string] = "";
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
        <Box
          sx={{
            width: "100%",
            margin: "10px 0px",
            "& .MuiAlert-root, & .MuiAlert-message": {
              width: "100%",
            },
            "& .MuiAlertTitle-root": {
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            },
          }}
        >
          <Alert
            icon={<FontAwesomeIcon icon={faInfo} size="2x" style={{ height: "30px" }} />}
            variant="outlined"
            severity="info"
          >
            <AlertTitle>
              {i18n.t("workspace:tinymce.shortcuts.instructionsTitle")}
              <Tooltip
                title={i18n.t("workspace:tinymce.shortcuts.toggleInstructions")}
                aria-label={i18n.t("workspace:tinymce.shortcuts.toggleInstructions")}
              >
                <IconButton onClick={() => this.toggleInstructions()}>
                  {this.state.instructions && <FontAwesomeIcon icon={faChevronUp} />}
                  {!this.state.instructions && <FontAwesomeIcon icon={faChevronDown} />}
                </IconButton>
              </Tooltip>
            </AlertTitle>
            {this.state.tab === 0 && this.state.instructions && (
              <TransRichText i18nKey="workspace:tinymce.shortcuts.instructions.actions.list" />
            )}
            {this.state.tab === 1 && this.state.instructions && (
              <TransRichText i18nKey="workspace:tinymce.shortcuts.instructions.symbols.list" />
            )}
            {this.state.instructions && (
              <p>
                <TransRichText i18nKey="workspace:tinymce.shortcuts.reservedShortcutsNote" />
              </p>
            )}
          </Alert>
        </Box>
        <Grid size={12}>
          <Tabs
            value={this.state.tab}
            onChange={this.handleChange}
            indicatorColor="primary"
            textColor="primary"
            sx={{ marginBottom: "15px" }}
            centered
          >
            <Tab label={i18n.t("workspace:tinymce.shortcuts.tabActions")} />
            <Tab label={i18n.t("workspace:tinymce.shortcuts.tabSymbols")} />
          </Tabs>
        </Grid>
        {this.state.tab === 0 && (
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
        {this.state.tab === 1 && (
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

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-shortcuts");
  const root = createRoot(domContainer as HTMLElement);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <MuiCssLayerProvider>
        <Shortcuts />
      </MuiCssLayerProvider>
    </I18nRoot>,
  );
});
