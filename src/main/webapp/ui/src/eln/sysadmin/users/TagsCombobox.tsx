import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import FilterIcon from "@mui/icons-material/FilterAlt";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Grow from "@mui/material/Grow";
import InputAdornment from "@mui/material/InputAdornment";
import ListItemText from "@mui/material/ListItemText";
import Popover from "@mui/material/Popover";
import { useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import useAutocomplete, {
  type AutocompleteCloseReason,
  type AutocompleteGroupedOption,
} from "@mui/material/useAutocomplete";
import type React from "react";
import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { List, type ListImperativeAPI, type RowComponentProps } from "react-window";
import axios from "@/common/axios";
import { checkUserInputString, helpText, isAllowed } from "../../../components/Tags/TagValidation";
import type RsSet from "../../../util/set";

/*
 * This component is a general purpose combobox for selecting a user tag. The
 * list of options is prepopulated based on the current set of tags associated
 * with users, but the user can also freely type in any tag that they wish.
 *
 * The component uses a virtualised list (using react-window) to prevent any
 * performance issues arising from displaying thousands of react nodes. A
 * virtualised list works by generating only the DOM nodes for the handful of
 * list items that the user can actually see at any given time, recalculating
 * as the user scrolls. The downside to this is that the list can momentarily
 * appear blank as the main thread tries to keep up because unlike a regular
 * list where the DOM nodes already exist, and thus the browser only needs to
 * perform layout and paint, JS must be executed and DOM nodes must be created
 * before the browser can perform layout and paint.
 *
 * The endpoint returns all of the tags at once, so the list is not paginated.
 *
 * This component was implemented using sample code and documentation from the following sites
 * https://mui.com/material-ui/react-autocomplete/#useautocomplete
 * https://github.com/mui/material-ui/blob/5046cc18373a169edbd75ef471245c23d8363fc9/docs/data/base/components/autocomplete/UseAutocomplete.js
 * https://github.com/mui/material-ui/blob/b0e10a1805ad7abd6f3c368bfbf63f4d85d29b47/packages/material-ui-lab/src/useAutocomplete/useAutocomplete.d.ts
 * https://react-window.vercel.app/#/examples/list/variable-size
 *
 * `useAutocomplete` validates, once on mount, that the ref returned by
 * `getInputProps` resolves to an <input> element (see
 * https://github.com/mui/material-ui/issues/28687). If the hook mounts while
 * its input is not yet in the DOM it logs "Unable to find the input element".
 * To guarantee the input is always present, the hook and the input it binds
 * to live together in `TagsComboboxContent`, which is only mounted while the
 * Popover is open, so the input is rendered in the same commit that mounts
 * the hook.
 */

/*
 * This is the width of the popup. Has to be a hard-coded value due to
 * react-window. Is designed to be wide enough for almost all tags, thereby
 * minimising the need for horizontal scrolling, whilst narrow enough for all
 * viewports. Measured in pixels.
 */
const POPOVER_WIDTH = 300;

/*
 * The height of an option in the pop-up menu. This is set to be the same as
 * all of the other menus in the UI, but has to be manually assigned for the
 * react-window library. Measured in pixels.
 */
const OPTION_HEIGHT = 36;

type InternalTag = {
  value: string;
  selected: boolean;
};

type UserTagRowProps = {
  groupedOptions: Array<InternalTag> | Array<AutocompleteGroupedOption<InternalTag>>;
  filter: string;
  keyboardFocusIndex: number | null;
  getOptionProps: (optionAndIndex: {
    option: InternalTag;
    index: number;
  }) => React.HTMLAttributes<HTMLLIElement> & { key: React.Key };
};

/*
 * Row renderer for the virtualised options list: a component passed as the
 * List's `rowComponent` (receiving `index`/`style` plus the List's `rowProps`);
 * defined at module scope so its identity stays stable across renders.
 */
function UserTagRow({
  index,
  style,
  groupedOptions,
  filter,
  keyboardFocusIndex,
  getOptionProps,
}: RowComponentProps<UserTagRowProps>) {
  const theme = useTheme();
  if (!groupedOptions || index >= groupedOptions.length) return <li style={style} />;

  const option = groupedOptions[index] as InternalTag;
  const name = option.value || "no name";
  const tagIsAllowed = !option.selected;

  const start = name.indexOf(filter);
  const end = start + filter.length;
  const label =
    start > -1 ? (
      <>
        {name.substring(0, start)}
        <strong>{filter}</strong>
        {name.substring(end)}
      </>
    ) : (
      name
    );

  /*
   * In MUI v9 getOptionProps returns a `key`. React requires keys to be passed
   * to JSX directly rather than spread in with the other props, so we pull it
   * out here and apply it explicitly.
   */
  const { key, ...optionProps } = getOptionProps({ option, index });

  return (
    // biome-ignore lint/a11y/useAriaPropsSupportedByRole: initial biome migration
    <li
      key={key}
      {...optionProps}
      style={{
        padding: "8px",
        cursor: "default",
        border: index === keyboardFocusIndex ? `2px solid ${theme.palette.primary.main}` : "none",
        backgroundColor: index === keyboardFocusIndex ? theme.palette.hover.iconButton : "transparent",
        borderRadius: "4px",
        // `style` positions the row within the virtualised list (absolute, with
        // top/left/height/width). Keep it after the static styles so it wins.
        ...style,
        // These depend on `option`, so must stay inline.
        filter: tagIsAllowed ? "" : "opacity(0.2)",
        pointerEvents: tagIsAllowed ? "auto" : "none",
        whiteSpace: "nowrap",
        width: "unset",
      }}
      data-tag-value={option.value}
      aria-selected={index === keyboardFocusIndex}
      aria-disabled={!tagIsAllowed}
    >
      <ListItemText primary={label} secondary={""} />
    </li>
  );
}

function OptionsListing({
  sortedOptions,
  getOptionProps,
  groupedOptions,
  listboxProps,
  listRef,
  keyboardFocusIndex,
  filter,
}: {
  sortedOptions: Array<InternalTag>;
  getOptionProps: (optionAndIndex: {
    option: InternalTag;
    index: number;
  }) => React.HTMLAttributes<HTMLLIElement> & { key: React.Key };
  groupedOptions: Array<InternalTag> | Array<AutocompleteGroupedOption<InternalTag>>;
  listboxProps: object;
  listRef: React.MutableRefObject<ListImperativeAPI | null>;
  keyboardFocusIndex: number | null;
  filter: string;
}) {
  // The endpoint returns all tags at once, so there is no infinite loading here;
  // a plain virtualised List with fixed-height rows suffices.
  return (
    <List
      {...listboxProps}
      // tagName makes the scroll container a <ul> so the <li> rows are valid.
      tagName="ul"
      style={{ height: 300, width: POPOVER_WIDTH }}
      rowCount={sortedOptions.length}
      listRef={listRef}
      rowComponent={UserTagRow}
      rowProps={{ groupedOptions, filter, keyboardFocusIndex, getOptionProps }}
      rowHeight={OPTION_HEIGHT}
    />
  );
}

type TagsComboboxArgs = {
  /*
   * This is the set of currently selected tags. It is a Set because this UI
   * component does not care about the order of the tags that the parent
   * component may or may not persist. This parameter is solely used to prevent
   * the user from choosing a tag that they have already selected.
   */
  value: RsSet<string>;

  /*
   * Identifies if the popup should be open and if so where it should be
   * positioned. If null then the popup is not shown. If an element then the
   * popup is shown adjacent to that element.
   */
  anchorEl: Element | null;

  /*
   * When the user chooses a tag from the suggestions, or enters a new one of
   * their own, this event handler is called. There is no mechanism for
   * removing tags from the selection within this component and providing such
   * functionality is the responsiblity of the parent component. It is expected
   * that the parent component should update the `value` prop above in this
   * callback by appending this new tag.
   */
  onSelection: (tagAsString: string) => void;

  /*
   * If the user chooses to close the popup, either with or without making a
   * selection, this event handler is called. It is expected that the parent
   * component will set the `anchorEl` prop to null as it is this that will
   * actually close the popup.
   */
  onClose: () => void;

  /*
   * Whether the user may enter a brand new tag of their own, in addition to
   * choosing from the suggestions. Defaults to true, which is appropriate when
   * tagging. When the combobox is used to filter by tag, pass false: only tags
   * that already exist can be filtered on, so the user is neither told to press
   * Enter to add a new tag nor able to do so.
   */
  allowNewTags?: boolean;
};

function TagsComboboxContent({
  onSelection,
  value,
  onClose,
  allowNewTags = true,
}: Omit<TagsComboboxArgs, "anchorEl">): React.ReactNode {
  const { t } = useTranslation("common");
  const [tags, setTags] = useState<Array<InternalTag>>([]);
  const [isNextPageLoading, setIsNextPageLoading] = useState(false);
  const [filter, setFilter] = useState("");
  const [error, setError] = useState(false);
  const [keyboardFocusIndex, setKeyboardFocusIndex] = useState<number | null>(null);
  const listRef = useRef<ListImperativeAPI | null>(null);

  const loadPage = async (): Promise<{
    tags: Array<InternalTag>;
    lastPage: boolean;
  }> => {
    if (filter.length < 2) return { tags: [], lastPage: true };
    setIsNextPageLoading(true);
    setError(false);
    try {
      const { data } = await axios.get<Array<string>>(`/system/users/allUserTags?tagFilter=${filter}`);
      return {
        lastPage: true,
        tags: data.map((tag) => ({
          value: tag,
          selected: value.has(tag),
        })),
      };
    } catch {
      setError(true);
      return {
        tags: [] as Array<InternalTag>,
        lastPage: true,
      };
    } finally {
      setIsNextPageLoading(false);
    }
  };

  const sortedOptions = useMemo(() => {
    return tags.toSorted((tagA, tagB) => {
      // sort all complete matches above all other suggestions
      if (tagA.value === filter) return -1;
      if (tagB.value === filter) return 1;
      const cmp = tagA.value.localeCompare(tagB.value);
      if (cmp < 0) return -1;
      if (cmp > 0) return 1;
      return 0;
    });
  }, [tags]);

  /*
   * useAutocomplete, a hook exposed by MUI, rather than the standard MUI
   * Autocomplete component is necessary because there is a scrolling bug when
   * virtualising the listbox (with react-window) inside the standard
   * Autocomplete.
   */
  const { getRootProps, getInputProps, getListboxProps, getOptionProps, groupedOptions } = useAutocomplete({
    open: true,
    options: sortedOptions,
    getOptionLabel: (option) => {
      // this can happen when user types in a filter
      if (typeof option === "string") return option;

      return option.value;
    },
    filterOptions: (x) => x,
    onInputChange: (_event, newInputValue, reason) => {
      if (reason === "input") {
        setFilter(newInputValue);
      }
    },
    onChange: () => {
      /*
       * Do nothing. If we wait for "onChange" to fire then the user has to tap
       * the tag twice: once to unfocus the filter textfield and a second time
       * to cause an "onChange" event to be dispatched with the details of the
       * selected tag. Instead, we rely on "onClose" to be fired when the
       * filter textfield loses focus, which happens to also include the
       * details of the tapped tag.
       */
    },
    onClose: (event: React.SyntheticEvent<Element, Event>, reason: AutocompleteCloseReason) => {
      /*
       * This event is fired whenever the user taps inside the Popover. There
       * are various different parts of the Popover that the user may tap on
       * and we want to choose just those for which a tag option -- i.e. an
       * <li> tag -- is the target.
       */
      let li = null;
      const relatedTarget = (event as React.FocusEvent).relatedTarget;
      if (event.currentTarget.nodeName === "INPUT" && relatedTarget?.nodeName === "LI") {
        li = relatedTarget;
      }
      if (event.currentTarget.nodeName === "LI") {
        li = event.currentTarget;
      }

      if ((reason === "blur" || reason === "selectOption") && li) {
        const { tagValue } = (li as HTMLLIElement).dataset;
        onSelection(tagValue as string);
        onClose();
      }
    },
  });
  const { ref: autocompleteInputRef, ...inputProps } = getInputProps();
  const inputRef = useRef<HTMLInputElement | null>(null);

  const setInputRef = (node: HTMLInputElement | null) => {
    inputRef.current = node;
    if (typeof autocompleteInputRef === "function") {
      autocompleteInputRef(node);
    } else if (autocompleteInputRef) {
      (autocompleteInputRef as React.MutableRefObject<HTMLInputElement | null>).current = node;
    }
  };

  /*
   * Whenever tags are added or removed from `value`, update the set of
   * available `tags` to disable the already selected ones
   */
  useEffect(() => {
    const alreadySelectedTagStrings = value;
    setTags(
      tags.map((tag) => ({
        ...tag,
        selected: alreadySelectedTagStrings.has(tag.value),
      })),
    );
  }, [value]);

  const [debounceTimeout, setDebounceTimeout] = useState<NodeJS.Timeout | null>(null);
  function debounce<FuncReturn>(func: () => FuncReturn, timeout: number = 1000): () => void {
    return () => {
      if (debounceTimeout) {
        clearTimeout(debounceTimeout);
      }
      setDebounceTimeout(setTimeout(() => func(), timeout));
    };
  }

  useEffect(() => {
    debounce(() => {
      // this assumes that the code that called `setFilter` also reset `page` to 0
      void loadPage().then(({ tags: newPageOfTags }) => {
        setTags(newPageOfTags);
      });
    })();
  }, [filter]);

  useEffect(() => {
    void loadPage().then(({ tags }) => setTags(tags));
  }, []);

  useEffect(() => {
    /*
     * This content component is only mounted while the Popover is open, so on
     * mount we focus the filter TextField so the user can type without having
     * to click again. The setTimeout defers the focus until after the Popover
     * transition has attached the input to the DOM.
     */
    const timer = setTimeout(() => {
      inputRef.current?.focus();
    }, 0);
    return () => clearTimeout(timer);
  }, []);

  const textFieldId = useId();
  return (
    <>
      <div
        {...getRootProps()}
        style={{
          padding: "8px",
          ...getRootProps().style,
        }}
      >
        <TextField
          variant="standard"
          label="Filter suggested tags"
          inputRef={setInputRef}
          onFocus={() => {
            /*
             * When the user taps on the "Add Tag" button we open the Popover
             * and focus this filter Textfield. When this happens, we want to
             * reset the state of the Popover by clearing the field and
             * reseting back to the first page so that the user can
             * immediately start a new filter.
             */
            if (filter !== "") {
              setFilter("");
            }
          }}
          onKeyDown={({ key }) => {
            if (key === "Enter") {
              if (keyboardFocusIndex !== null) {
                const chosenTag = sortedOptions[keyboardFocusIndex];
                onSelection(chosenTag.value);
                setKeyboardFocusIndex(null);
                onClose();
                return;
              }

              /*
               * In addition to selecting a tag from the menu, users can also
               * enter any tag they like -- unless the combobox is being used to
               * filter (allowNewTags === false), in which case only a tag that
               * already exists may be chosen (e.g. by typing its exact name).
               */
              const tagAlreadyExists = sortedOptions.some((t) => t.value === filter);
              if ((allowNewTags || tagAlreadyExists) && isAllowed(checkUserInputString(filter))) {
                onSelection(filter);
                setKeyboardFocusIndex(null);
                onClose();
              }
              return;
            }

            if (key === "Escape") {
              setKeyboardFocusIndex(null);
              onClose();
              return;
            }

            // focus next allowed tag; do nothing if there are no more
            if (key === "ArrowDown") {
              let newIndex = keyboardFocusIndex ?? -1;
              do {
                newIndex++;
                if (newIndex === tags.length - 1 && sortedOptions[newIndex].selected) {
                  newIndex = keyboardFocusIndex ?? 0;
                  break;
                }
              } while (sortedOptions[newIndex].selected);
              setKeyboardFocusIndex(newIndex);
              listRef.current?.scrollToRow({ index: newIndex });
              return;
            }

            // focus previous allowed tag; do nothing if on first allowed
            if (key === "ArrowUp") {
              let newIndex = keyboardFocusIndex ?? tags.length;
              do {
                newIndex--;
                if (newIndex === 0 && sortedOptions[newIndex].selected) {
                  newIndex = keyboardFocusIndex ?? 0;
                  break;
                }
              } while (sortedOptions[newIndex].selected);
              setKeyboardFocusIndex(newIndex);
              listRef.current?.scrollToRow({ index: newIndex });
              return;
            }

            // any other key resets keyboard focus as filter has changed
            setKeyboardFocusIndex(null);
          }}
          error={!isAllowed(checkUserInputString(filter))}
          helperText={helpText(checkUserInputString(filter))}
          tabIndex={0}
          fullWidth
          value={filter}
          sx={{
            fontSize: "1.1em",
          }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <FilterIcon />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="start">
                  {/* Because the icon is so small, the animations need to be
                   * more exagerated, hence the timeout and animationDuration
                   */}
                  <Grow in={isNextPageLoading} timeout={300}>
                    <div>
                      <FontAwesomeIcon icon={faSpinner} spin size="sm" style={{ animationDuration: "1.5s" }} />
                    </div>
                  </Grow>
                </InputAdornment>
              ),
            },

            htmlInput: {
              ...inputProps,
              id: textFieldId,
              value: filter,
            },

            inputLabel: {
              htmlFor: textFieldId,
            },
          }}
        />
      </div>
      {groupedOptions.length > 0 && (
        <OptionsListing
          sortedOptions={sortedOptions}
          getOptionProps={getOptionProps}
          groupedOptions={groupedOptions}
          listboxProps={getListboxProps()}
          listRef={listRef}
          keyboardFocusIndex={keyboardFocusIndex}
          filter={filter}
        />
      )}
      {!error && groupedOptions.length === 0 && filter.length > 1 && (
        <Alert severity="info">
          <AlertTitle>{t("tags.noMatchingSuggestions")}</AlertTitle>
          {allowNewTags && t("tags.useNewTagHint")}
        </Alert>
      )}
      {error && (
        <Alert severity="warning">
          <AlertTitle>{t("tags.errorFetchingTags")}</AlertTitle>
          {allowNewTags ? t("tags.typeTagInstead") : t("tags.tryAgain")}
        </Alert>
      )}
    </>
  );
}

export default function TagsCombobox({
  onSelection,
  value,
  anchorEl,
  onClose,
  allowNewTags,
}: TagsComboboxArgs): React.ReactNode {
  return (
    <Popover
      onClose={onClose}
      open={Boolean(anchorEl)}
      anchorEl={anchorEl}
      anchorOrigin={{
        vertical: "top",
        horizontal: "right",
      }}
      transformOrigin={{
        vertical: "top",
        horizontal: "left",
      }}
      elevation={0}
      transitionDuration={window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : "auto"}
      slotProps={{
        backdrop: {
          invisible: false,
          transitionDuration: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 225,
        },

        paper: {
          variant: "outlined",
          style: {
            padding: "4px",
            paddingBottom: "12px",
            width: POPOVER_WIDTH,
          },
        },
      }}
    >
      {Boolean(anchorEl) && (
        <TagsComboboxContent value={value} onSelection={onSelection} onClose={onClose} allowNewTags={allowNewTags} />
      )}
    </Popover>
  );
}
