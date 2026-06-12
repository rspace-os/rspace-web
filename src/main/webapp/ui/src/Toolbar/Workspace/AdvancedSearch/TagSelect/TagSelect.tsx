import Box from "@mui/material/Box";
import { buttonBaseClasses } from "@mui/material/ButtonBase";
import { chipClasses } from "@mui/material/Chip";
import { inputBaseClasses } from "@mui/material/InputBase";
import ListItemButton from "@mui/material/ListItemButton";
import Paper from "@mui/material/Paper";
import { useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React, { useEffect } from "react";
import Select, {
  type ControlProps,
  type GroupBase,
  type MenuProps,
  type MultiValueGenericProps,
  type NoticeProps,
  type OptionProps,
  type StylesConfig,
  type ValueContainerProps,
} from "react-select";
import axios from "@/common/axios";

type TagOption = {
  value: string;
  label: string;
};

type TagSelectProps = {
  advanced?: boolean;
  error?: string;
  selected?: string;
  testId?: string;
  updateSelected: (value: Array<TagOption> | null, key: string) => void;
};

type TagSelectExtraProps = {
  testId?: string;
  TextFieldProps?: Record<string, unknown>;
};

type TagSelectGroup = GroupBase<TagOption>;
type TagControlProps = ControlProps<TagOption, true, TagSelectGroup>;
type TagNoticeProps = NoticeProps<TagOption, true, TagSelectGroup>;
type TagOptionProps = OptionProps<TagOption, true, TagSelectGroup>;
type TagValueContainerProps = ValueContainerProps<TagOption, true, TagSelectGroup>;
type TagMenuProps = MenuProps<TagOption, true, TagSelectGroup>;
type TagMultiValueProps = MultiValueGenericProps<TagOption, true, TagSelectGroup>;
type InputRef = React.Ref<HTMLDivElement>;

function NoOptionsMessage(props: TagNoticeProps) {
  return (
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
<Typography color="textSecondary" sx={{ p: "8px 16px" }} {...(props.innerProps as any)}>
      {props.children}
    </Typography>
  );
}

const inputComponent = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement> & { inputRef?: InputRef }>(
  ({ inputRef, ...props }, ref) => <div ref={inputRef || ref} {...props} />,
);

inputComponent.displayName = "TagSelectInput";

function Control(props: TagControlProps) {
  const { children, innerProps, innerRef } = props;
  const selectProps = props.selectProps as TagControlProps["selectProps"] & TagSelectExtraProps;
  const { TextFieldProps } = selectProps;

  return (
    <TextField
      variant="standard"
      data-test-id={selectProps.testId}
      className="search-input"
      fullWidth
      {...TextFieldProps}
      slotProps={{
        input: {
          inputComponent,
          inputProps: {
            style: { display: "flex", padding: 0, height: "100%" },
            ref: innerRef,
            children,
            ...innerProps,
          },
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        } as any,
      }}
    />
  );
}

function Option(props: TagOptionProps) {
  return (
    <ListItemButton
      data-test-id={`a-search-tag-option-${props.children}`}
      className="dropdown-item"
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      ref={props.innerRef as any}
      selected={props.isFocused}
      component="div"
      sx={{ fontWeight: props.isSelected ? 500 : 400 }}
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      {...(props.innerProps as any)}
    >
      {props.children}
    </ListItemButton>
  );
}

function ValueContainer(props: TagValueContainerProps) {
  return (
    <Box
      sx={{
        display: "flex",
        flexWrap: "wrap",
        flex: 1,
        alignItems: "center",
        overflow: "hidden",
      }}
    >
      {props.children}
    </Box>
  );
}

function MultiValue(props: TagMultiValueProps) {
  return <span>{`${props.children}, `}</span>;
}

function Menu(props: TagMenuProps) {
  return (
    <Paper
      square
      sx={{
        position: "absolute",
        zIndex: 1,
        width: "fit-content !important",
        mt: 1,
        left: 0,
        right: 0,
      }}
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      {...(props.innerProps as any)}
    >
      {props.children}
    </Paper>
  );
}

const components = {
  Control,
  Menu,
  MultiValue,
  NoOptionsMessage,
  Option,
  ValueContainer,
  DropdownIndicator: null,
};

const RSPACE_ONTOLOGY_URL_DELIMITER = "__RSP_EXTONT_URL_DELIM__";

const replaceCommaDelimiterInTag = (tag: string) => tag.replaceAll("__rspactags_comma__", ",");

const replaceForwardSlashInTag = (tag: string) => tag.replaceAll("/", "__rspactags_forsl__");

const parseDelimitedTags = (tag: string) =>
  tag.includes(RSPACE_ONTOLOGY_URL_DELIMITER)
    ? replaceCommaDelimiterInTag(tag.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0].trim())
    : tag;

export default function TagSelect(props: TagSelectProps) {
  const theme = useTheme();
  const [multi, setMulti] = React.useState<Array<TagOption> | null>(null);
  const [suggestions, setSuggestions] = React.useState<Array<TagOption>>([]);

  function handleChangeMulti(value: ReadonlyArray<TagOption> | null) {
    const selectedValues = value ? [...value] : null;
    setMulti(selectedValues);
    props.updateSelected(selectedValues, "value");
  }

  const selectStyles: StylesConfig<TagOption, true, TagSelectGroup> = {
    container: (base) => ({
      ...base,
      width: "100%",
    }),
    input: (base) => ({
      ...base,
      color: theme.palette.text.primary,
    }),
  };

  useEffect(() => {
    const fetchData = async () => {
      const result = await axios("/workspace/editor/structuredDocument/userTags");

      const tags = (result.data.data as Array<string>).map((suggestion) => {
        const label = parseDelimitedTags(suggestion);
        return { value: replaceForwardSlashInTag(label), label };
      });
      setSuggestions(tags);

      if (props.selected) {
        const localSelected = props.selected
          .split("<<>>")
          .map((selectedLabel) => tags.find((tag) => tag.label === selectedLabel))
          .filter((tag): tag is TagOption => Boolean(tag));
        handleChangeMulti(localSelected);
      }
    };
    fetchData();
  }, [props.selected]);

  return (
    <Box
      sx={{
        display: "flex",
        flexGrow: 1,
        "& .myReactSelect .Select-arrow-zone": {
          display: "none",
        },
        "& .advanced-search": {
          [`& .search-input .${inputBaseClasses.input}`]: {
            height: 32,
          },
          "& .dropdown-item": {
            margin: 0,
            width: "100%",
          },
        },
        "& .simple-search": {
          "& .search-input": {
            marginTop: "3px",
            minHeight: "36px",
          },
        },
        [`& .${buttonBaseClasses.root}:not(.${chipClasses.root})`]: {
          width: "fit-content !important",
          fontSize: 15,
          marginLeft: "10px",
        },
      }}
    >
      <Select<TagOption, true, TagSelectGroup>
        className={props.advanced ? "advanced-search" : "simple-search"}
        styles={selectStyles}
        options={suggestions}
        components={components}
        value={multi}
        onChange={handleChangeMulti}
        isMulti
        placeholder={props.error || "Select tag(s)"}
        // @ts-expect-error pragmatic jsx->tsx conversion: testId is forwarded via selectProps, not a typed react-select prop
        testId={props.testId}
      />
    </Box>
  );
}
