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
import { List, type ListImperativeAPI, type RowComponentProps } from "react-window";
import { useInfiniteLoader } from "react-window-infinite-loader";
import type { Tag } from "../../stores/definitions/Tag";
import { lift3, Optional } from "../../util/optional";
import type RsSet from "../../util/set";
import { FINAL_DATA_SIGNAL, parseEncodedTags, SMALL_DATASET_SIGNAL } from "./ParseEncodedTagStrings";
import { checkInternalTag, checkUserInputString, helpText, isAllowed } from "./TagValidation";

/*
 * This component is a general purpose combobox for selecting a tag. The list
 * of options is prepopulated based on the ontologies feature, but the user can
 * also freely type in any tag that they wish.
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
 * Moreover, this combobox also uses infinite loading (using
 * react-window-infinite-loader) to load the list of suggested tags in pages,
 * loading the next page as the user approaches the end of the list. This,
 * again, reduces performance issues by reducing the size of the network calls.
 *
 * This component was implemented using sample code and documentation from the following sites
 * https://mui.com/material-ui/react-autocomplete/#useautocomplete
 * https://github.com/mui/material-ui/blob/5046cc18373a169edbd75ef471245c23d8363fc9/docs/data/base/components/autocomplete/UseAutocomplete.js
 * https://github.com/mui/material-ui/blob/b0e10a1805ad7abd6f3c368bfbf63f4d85d29b47/packages/material-ui-lab/src/useAutocomplete/useAutocomplete.d.ts
 * https://react-window.vercel.app/#/examples/list/variable-size
 * https://www.npmjs.com/package/react-window-infinite-loader
 *
 * IMPORTANT: MUI's useAutocomplete validates on mount that its <input> element
 * is present in the DOM, and immediately operates on the input's ref. Because
 * the input is rendered inside a Popover (which mounts its contents through a
 * Portal, one commit after the Popover-owning component), useAutocomplete must
 * NOT be called by the Popover-owning component -- on mount its input would not
 * yet exist, logging a "missing input ref" error and crashing on a null ref
 * (see https://github.com/mui/material-ui/issues/28687). The export below
 * therefore splits responsibilities: TagsCombobox owns the Popover, while the
 * inner TagsComboboxContent (rendered as a child of the Popover) calls
 * useAutocomplete, so the hook and its <input> mount together.
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

type InternalTag = Tag & {
  selected: boolean;
};

type TagRowProps = {
  sortedOptions: Array<InternalTag>;
  groupedOptions: Array<InternalTag> | Array<AutocompleteGroupedOption<InternalTag>>;
  isNextPageLoading: boolean;
  hasNextPage: boolean;
  enforceOntologies: boolean;
  filter: string;
  keyboardFocusIndex: number | null;
  getOptionProps: (optionAndIndex: {
    option: InternalTag;
    index: number;
  }) => React.HTMLAttributes<HTMLLIElement> & { key: React.Key };
};

/*
 * Row renderer for the virtualised options list. In react-window v2 the row is
 * a component passed as `rowComponent`, receiving `index`/`style` plus the
 * values supplied via the List's `rowProps`. Defined at module scope so its
 * identity is stable across renders (otherwise the list remounts every row).
 */
function TagRow({
  index,
  style,
  sortedOptions,
  groupedOptions,
  isNextPageLoading,
  hasNextPage,
  enforceOntologies,
  filter,
  keyboardFocusIndex,
  getOptionProps,
}: RowComponentProps<TagRowProps>) {
  const theme = useTheme();
  const isItemLoaded = !hasNextPage || index < sortedOptions.length;
  if (!isItemLoaded && isNextPageLoading) {
    return <li style={style}>Loading...</li>;
  }
  if (!groupedOptions || index >= groupedOptions.length) return <li style={style} />;

  const option = groupedOptions[index] as InternalTag;
  const name = option.value || "no name";
  const tagIsAllowed = isAllowed(checkInternalTag(option, { enforceOntologies }));

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
        backgroundColor: index === keyboardFocusIndex ? theme.palette.hover.iconButton : "default",
        borderRadius: "4px",
        // `style` positions the row within the virtualised list (absolute, with
        // top/left/height/width). Keep it after the static styles so it wins.
        ...style,
        // These depend on `option`, so must stay inline (a class-based width
        // would otherwise be overridden by the positioning `style` above).
        filter: tagIsAllowed ? "" : "opacity(0.2)",
        pointerEvents: tagIsAllowed ? "auto" : "none",
        whiteSpace: "nowrap",
        width: "unset",
      }}
      data-tag-value={option.value}
      data-tag-vocabulary={option.vocabulary.orElse("")}
      data-tag-uri={option.uri.orElse("")}
      data-tag-version={option.version.orElse("")}
      aria-selected={index === keyboardFocusIndex}
      aria-disabled={!tagIsAllowed}
    >
      {/*
       * No checkboxes here: in some places tags are persisted as a plain array
       * or comma-separated string, so when the suggestions contain multiple
       * tags with the same name (e.g. different ontology versions) we cannot
       * identify which one the user originally chose, and so cannot show a
       * checked state against just that suggestion. Instead we disable any
       * suggestion whose name matches an already-selected tag; removing tags is
       * the parent component's responsibility.
       */}
      <ListItemText primary={label} secondary={helpText(checkInternalTag(option, { enforceOntologies }))} />
    </li>
  );
}

function OptionsListing({
  hasNextPage,
  isNextPageLoading,
  sortedOptions,
  loadNextPage,
  getOptionProps,
  groupedOptions,
  listboxProps,
  listRef,
  keyboardFocusIndex,
  filter,
  enforceOntologies,
}: {
  hasNextPage: boolean;
  isNextPageLoading: boolean;
  loadNextPage: () => void;
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
  enforceOntologies: boolean;
}) {
  const itemCount = hasNextPage ? sortedOptions.length + 1 : sortedOptions.length;
  const isItemLoaded = (index: number) => !hasNextPage || index < sortedOptions.length;
  // react-window-infinite-loader v2 exposes a hook that returns the
  // onRowsRendered callback to wire into the List (loadMoreRows returns a
  // Promise resolved once loading completes).
  const loadMoreRows = (): Promise<void> => {
    if (!isNextPageLoading) loadNextPage();
    return Promise.resolve();
  };
  const onRowsRendered = useInfiniteLoader({
    isRowLoaded: isItemLoaded,
    loadMoreRows,
    rowCount: itemCount,
  });

  return (
    <List
      {...listboxProps}
      // tagName makes the scroll container a <ul> so the <li> rows are valid
      // (react-window v2 dropped innerElementType in favour of tagName).
      tagName="ul"
      style={{ height: 300, width: POPOVER_WIDTH }}
      rowCount={itemCount}
      onRowsRendered={onRowsRendered}
      listRef={listRef}
      rowComponent={TagRow}
      rowProps={{
        sortedOptions,
        groupedOptions,
        isNextPageLoading,
        hasNextPage,
        enforceOntologies,
        filter,
        keyboardFocusIndex,
        getOptionProps,
      }}
      /*
       * Allocates the height for each tag by index. Most tags get OPTION_HEIGHT;
       * those showing helper text need more. The virtualised list needs explicit
       * heights to compute vertical offsets (CSS flexbox can't be used).
       * react-window v2 re-reads this function when `rowProps` change, so heights
       * recalculate when the tag list changes — the v1
       * `listRef.current.resetAfterIndex(0)` calls are no longer needed.
       *
       * `i` may exceed `sortedOptions.length` while the "Loading..." placeholder
       * shows, so `Optional.fromNullable(sortedOptions.at(i))`/`orElse` guard
       * against undefined.
       */
      rowHeight={(i) =>
        OPTION_HEIGHT +
        Optional.fromNullable(sortedOptions.at(i))
          .map((tag) => {
            const tagHasHelpText = helpText(checkInternalTag(tag, { enforceOntologies })) === null;
            return tagHasHelpText ? 0 : 20;
          })
          .orElse(0)
      }
    />
  );
}

/**
 * The type parameter `Toggle` toggles the component betwen enforcing and not
 * enforcing ontologies. When not enforced, user can select any tag and enter
 * their own new tag, but when enforced the user must choose one from the set
 * of suggestions. When choosing one from the ontology files, there will be
 * additional metadata, and so when ontologies are enforced `onSelection` will
 * always return an object that has that metadata. On the other hand, when
 * ontologies are not enforced this metadata may or may not be available.
 */
type TagsComboboxArgs<
  Toggle extends
    | {
        enforce: true;
        tag: {
          value: string;
          vocabulary: string;
          uri: string;
          version: string;
        };
      }
    | {
        enforce: false;
        tag: {
          value: string;
          vocabulary: Optional<string>;
          uri: Optional<string>;
          version: Optional<string>;
        };
      },
> = {
  /*
   * Sets which branch of the Toggle type that is being applied, and therefore
   * sets the type of what `onSelection` is called with.
   */
  enforceOntologies: Toggle["enforce"];

  /*
   * This is the set of currently selected tags. It is a Set because this UI
   * component does not care about the order of the tags that the parent
   * component may or may not persist. This parameter is solely used to prevent
   * the user from choosing a tag that they have already selected.
   */
  value: RsSet<Toggle["tag"]>;

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
   * callback by appending this new tag. The precise type of the tag object
   * passed to `onSelection` is determined based on whether ontologies are
   * being enforced and thus whether the additional metadata is certain to be
   * available.
   */
  onSelection: (selectedTag: Toggle["tag"]) => void;

  /*
   * If the user chooses to close the popup, either with or without making a
   * selection, this event handler is called. It is expected that the parent
   * component will set the `anchorEl` prop to null as it is this that will
   * actually close the popup.
   */
  onClose: () => void;
};

/*
 * The autocomplete-driven body of the combobox. This is deliberately a
 * separate component, rendered *inside* the Popover (see TagsCombobox below),
 * rather than being part of the Popover-owning component. MUI's
 * useAutocomplete validates on mount that its <input> is in the DOM and
 * immediately operates on that input's ref (e.g. to clear
 * aria-activedescendant). The Popover renders its contents through a Portal,
 * which mounts them one commit *after* the Popover-owning component itself; if
 * useAutocomplete lived in that owning component it would run its on-mount
 * effects before the input existed, logging a "missing input ref" error (and,
 * with the popup open, crashing on a null ref). Keeping useAutocomplete here,
 * as a child of the Popover, guarantees the hook and its <input> mount in the
 * same commit.
 */
function TagsComboboxContent<
  Toggle extends
    | {
        enforce: true;
        tag: {
          value: string;
          vocabulary: string;
          uri: string;
          version: string;
        };
      }
    | {
        enforce: false;
        tag: {
          value: string;
          vocabulary: Optional<string>;
          uri: Optional<string>;
          version: Optional<string>;
        };
      },
>({ onSelection, value, anchorEl, onClose, enforceOntologies }: TagsComboboxArgs<Toggle>): React.ReactNode {
  const [tags, setTags] = useState<Array<InternalTag>>([]);
  const [isNextPageLoading, setIsNextPageLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState("");
  const [reachedEnd, setReachedEnd] = useState(false);
  const [error, setError] = useState(false);
  const [keyboardFocusIndex, setKeyboardFocusIndex] = useState<number | null>(null);
  const listRef = useRef<ListImperativeAPI | null>(null);

  const loadPage = (): Promise<{
    tags: Array<InternalTag>;
    lastPage: boolean;
  }> => {
    if (reachedEnd) return Promise.resolve({ tags: [], lastPage: true });
    setIsNextPageLoading(true);
    setError(false);
    return fetch(`/workspace/editor/structuredDocument/userTagsAndOntologies?pos=${page}&tagFilter=${filter}`)
      .then((response) => response.json())
      .then(({ data }: { data: Array<string> }) => {
        const alreadySelectedTagValues = value.map((v) => v.value);
        return {
          lastPage: data.includes(SMALL_DATASET_SIGNAL) || data.includes(FINAL_DATA_SIGNAL),
          tags: parseEncodedTags(data).map(
            (tag: Tag): InternalTag => ({
              ...tag,
              selected: alreadySelectedTagValues.has(tag.value),
            }),
          ),
        };
      })
      .catch(() => {
        setError(true);
        return {
          tags: [] as Array<InternalTag>,
          lastPage: true,
        };
      })
      .finally(() => {
        setIsNextPageLoading(false);
      });
  };

  const loadNextPage = () => {
    void loadPage().then(({ lastPage, tags: newPageOfTags }) => {
      setReachedEnd(lastPage);
      setTags([...tags, ...newPageOfTags]);
      if (!lastPage) setPage(page + 1);
    });
  };

  const sortedOptions = useMemo(() => {
    /*
     * We don't want to sort the tags if a filter has not been applied as the
     * API returns all of the tags beginning with an upper case letter, sorted
     * alphabetically, followed by all of those beginning with a lower case
     * letter. If we were to sort those, the user's scroll position would
     * become confused when transitioning from the upper to the lower case
     * blocks, as new tags were inserted above the user's scroll position. If
     * the tags ought to be presented in alphabetical order then this should be
     * done on the server side.
     */
    if (!filter) return tags;

    /*
     * Once the user has applied a filter search term, we can assume that the
     * entire set of search results will fit into a single page of the API's
     * response, which at time of writing is 1000 tags, so applying client-side
     * sorting is a safe operation.
     */
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
   * useAutocomplete, a hook exposes my MUI, rather than the standard MUI
   * Autocomplete component is necessary because there is a scrolling bug with
   * using react-window-infinite-loader with Autocomplete. See this
   * StackOverflow post for more info
   * https://stackoverflow.com/questions/59013367/react-window-infinite-loader-material-ui-autocomplete
   */
  const { getRootProps, getInputProps, getListboxProps, getOptionProps, groupedOptions } = useAutocomplete({
    open: Boolean(anchorEl),
    options: sortedOptions,
    getOptionLabel: (option) => {
      // this can happen when user types in a filter
      if (typeof option === "string") return option;

      return option.value;
    },
    filterOptions: (x) => x,
    onInputChange: (_event, newInputValue, reason) => {
      setPage(0);
      setReachedEnd(false);
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
        const { tagValue, tagVocabulary, tagUri, tagVersion } = (li as HTMLLIElement).dataset;
        if (enforceOntologies) {
          if (tagVocabulary === "") throw new Error("Missing tag's vocabulary");
          if (tagUri === "") throw new Error("Missing tag's vocabulary URI");
          if (tagVersion === "") throw new Error("Missing tag's version");
          onSelection({
            value: tagValue as string,
            vocabulary: tagVocabulary as string,
            uri: tagUri as string,
            version: tagVersion as string,
          });
        } else {
          onSelection({
            value: tagValue as string,
            vocabulary: tagVocabulary === "" ? Optional.empty() : Optional.present(tagVocabulary as string),
            uri: tagUri === "" ? Optional.empty() : Optional.present(tagUri as string),
            version: tagVersion === "" ? Optional.empty() : Optional.present(tagVersion as string),
          });
        }
        onClose();
      }
    },
  });
  /*
   * Local ref to the filter <input>, used to focus the field when the Popover
   * opens (see the `anchorEl` effect below). It is forked with
   * useAutocomplete's own input ref (carried on `getInputProps().ref`) by
   * MUI's InputBase: the local ref is passed via the TextField `inputRef`
   * prop, while the autocomplete ref is spread into `slotProps.htmlInput`.
   * Both are stable across renders, so the input node is attached to each
   * without the ref churn that previously left useAutocomplete's ref null when
   * it ran its on-mount "input element present" validation.
   */
  const inputRef = useRef<HTMLInputElement | null>(null);

  /*
   * Whenever tags are added or removed from `value`, update the set of
   * available `tags` to disable the already selected ones
   */
  useEffect(() => {
    const alreadySelectedTagStrings = value.map((v) => v.value);
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
      void loadPage().then(({ lastPage, tags: newPageOfTags }) => {
        setTags(newPageOfTags);
        setPage(1);
        setReachedEnd(lastPage);
      });
    })();
  }, [filter]);

  useEffect(() => {
    loadNextPage();
  }, []);

  useEffect(() => {
    if (anchorEl) {
      /*
       * After opening the Popover, focus the filter TextField so that
       * the user can just type without having to click again. It't not
       * clear why the setTimeout is necessary, given that `keepMounted`
       * is set in the Popover and `getInputProps().ref.current` should
       * already be set.
       */
      setTimeout(() => {
        inputRef.current?.focus();
      }, 0);
    }
  }, [anchorEl]);

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
          inputRef={inputRef}
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
              setPage(0);
              setReachedEnd(false);
            }
          }}
          onKeyDown={({ key }) => {
            if (key === "Enter") {
              if (keyboardFocusIndex !== null) {
                const chosenTag = sortedOptions[keyboardFocusIndex];
                if (enforceOntologies) {
                  lift3(
                    (vocabulary: string, uri: string, version: string) => ({
                      value: chosenTag.value,
                      vocabulary,
                      uri,
                      version,
                    }),
                    chosenTag.vocabulary,
                    chosenTag.uri,
                    chosenTag.version,
                  ).map((newTag) => onSelection(newTag));
                } else {
                  onSelection({
                    value: chosenTag.value,
                    vocabulary: chosenTag.vocabulary,
                    uri: chosenTag.uri,
                    version: chosenTag.version,
                  });
                }
                setKeyboardFocusIndex(null);
                onClose();
                return;
              }

              /*
               * In addition to selecting a tag from the menu, users can also
               * enter any tag they like when ontologies are not being
               * enforced.
               */
              if (isAllowed(checkUserInputString(filter))) {
                if (!enforceOntologies) {
                  onSelection({
                    value: filter,
                    vocabulary: Optional.empty(),
                    uri: Optional.empty(),
                    version: Optional.empty(),
                  });
                  setKeyboardFocusIndex(null);
                  onClose();
                }
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
                if (
                  newIndex === tags.length - 1 &&
                  !isAllowed(
                    checkInternalTag(sortedOptions[newIndex], {
                      enforceOntologies,
                    }),
                  )
                ) {
                  newIndex = keyboardFocusIndex ?? 0;
                  break;
                }
              } while (
                !isAllowed(
                  checkInternalTag(sortedOptions[newIndex], {
                    enforceOntologies,
                  }),
                )
              );
              setKeyboardFocusIndex(newIndex);
              listRef.current?.scrollToRow({ index: newIndex });
              return;
            }

            // focus previous allowed tag; do nothing if on first allowed
            if (key === "ArrowUp") {
              let newIndex = keyboardFocusIndex ?? tags.length;
              do {
                newIndex--;
                if (
                  newIndex === 0 &&
                  !isAllowed(
                    checkInternalTag(sortedOptions[newIndex], {
                      enforceOntologies,
                    }),
                  )
                ) {
                  newIndex = keyboardFocusIndex ?? 0;
                  break;
                }
              } while (
                !isAllowed(
                  checkInternalTag(sortedOptions[newIndex], {
                    enforceOntologies,
                  }),
                )
              );
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
              ...getInputProps(),
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
          hasNextPage={!reachedEnd}
          isNextPageLoading={isNextPageLoading}
          sortedOptions={sortedOptions}
          loadNextPage={loadNextPage}
          getOptionProps={getOptionProps}
          groupedOptions={groupedOptions}
          listboxProps={getListboxProps()}
          listRef={listRef}
          keyboardFocusIndex={keyboardFocusIndex}
          filter={filter}
          enforceOntologies={enforceOntologies}
        />
      )}
      {!error && groupedOptions.length === 0 && filter === "" && (
        <Alert severity="info">
          <AlertTitle>No tags available</AlertTitle>
        </Alert>
      )}
      {!error && groupedOptions.length === 0 && filter !== "" && (
        <Alert severity="info">
          <AlertTitle>No matching tag suggestions {enforceOntologies ? "from ontologies" : ""}.</AlertTitle>
          {/** biome-ignore lint/complexity/noUselessFragments: initial biome migration */}
          {enforceOntologies ? <></> : <>To use a new tag, press Enter.</>}
        </Alert>
      )}
      {error && (
        <Alert severity="warning">
          <AlertTitle>Error fetching tags</AlertTitle>
          {enforceOntologies ? (
            <>Please check that the ontology files are correctly configured.</>
          ) : (
            <>Simply type in the tag and press enter instead.</>
          )}
        </Alert>
      )}
    </>
  );
}

/**
 * A general-purpose combobox for selecting a tag, shown in a Popover anchored
 * to `anchorEl`. The list of suggestions is prepopulated from the ontologies
 * feature; unless ontologies are enforced the user may also type any tag.
 *
 * The actual autocomplete UI lives in {@link TagsComboboxContent}, which is
 * rendered as a child of the Popover so that MUI's useAutocomplete hook and the
 * <input> it manages mount together in the same commit (see the comment on
 * TagsComboboxContent for why this matters).
 */
export default function TagsCombobox<
  Toggle extends
    | {
        enforce: true;
        tag: {
          value: string;
          vocabulary: string;
          uri: string;
          version: string;
        };
      }
    | {
        enforce: false;
        tag: {
          value: string;
          vocabulary: Optional<string>;
          uri: Optional<string>;
          version: Optional<string>;
        };
      },
>(props: TagsComboboxArgs<Toggle>): React.ReactNode {
  const { anchorEl, onClose } = props;
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
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
      keepMounted
      transitionDuration={reduceMotion ? 0 : "auto"}
      slotProps={{
        backdrop: {
          invisible: false,
          transitionDuration: reduceMotion ? 0 : 225,
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
      {/*
       * Only mount the autocomplete content while the Popover is open. This
       * keeps useAutocomplete (and its on-mount input validation) from running
       * while there is no input to bind to.
       */}
      {Boolean(anchorEl) && <TagsComboboxContent {...props} />}
    </Popover>
  );
}
