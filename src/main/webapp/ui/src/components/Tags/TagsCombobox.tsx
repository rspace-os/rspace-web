import React, { useEffect, useState, useRef, useMemo, useId } from "react";
import TextField from "@mui/material/TextField";
import useAutocomplete, {
  AutocompleteCloseReason,
  AutocompleteGroupedOption,
} from "@mui/material/useAutocomplete";
import { VariableSizeList as List, VariableSizeList } from "react-window";
import InfiniteLoader from "react-window-infinite-loader";
import Popover from "@mui/material/Popover";
import InputAdornment from "@mui/material/InputAdornment";
import { StyledMenuItem } from "../StyledMenu";
import FilterIcon from "@mui/icons-material/FilterAlt";
import ListItemText from "@mui/material/ListItemText";
import * as ArrayUtils from "../../util/ArrayUtils";
import {
  checkUserInputString,
  checkInternalTag,
  helpText,
  isAllowed,
} from "./TagValidation";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { makeStyles } from "tss-react/mui";
import { Optional, lift3 } from "../../util/optional";
import { stableSort } from "../../util/table";
import { type Tag } from "../../stores/definitions/Tag";
import RsSet from "../../util/set";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons";
library.add(faSpinner);
import Grow from "@mui/material/Grow";
import {
  parseEncodedTags,
  SMALL_DATASET_SIGNAL,
  FINAL_DATA_SIGNAL,
} from "./ParseEncodedTagStrings";

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
 * Note that there is a bug with useAutocomplete that makes it report an
 * error to the JS console about a missing <input> ref. This is an issue that
 * is not easy to resolve, but doesn't appear to cause any issues. See
 * this discussions:
 * https://github.com/mui/material-ui/issues/28687
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

/*
 * makeStyles is used because withStyles is not performant enough to render the
 * menu items as the user scrolls the virtualised list
 */
const useStyles = makeStyles<{
  index: number;
  keyboardFocusIndex: number | null;
}>()((theme, { index, keyboardFocusIndex }) => ({
  menuItem: {
    padding: "8px",
    cursor: "default",

    border:
      index === keyboardFocusIndex
        ? `2px solid ${theme.palette.primary.main}`
        : "none",
    backgroundColor:
      index === keyboardFocusIndex ? theme.palette.hover.iconButton : "default",
    borderRadius: "4px",
  },
}));

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
  }) => object;
  groupedOptions:
    | Array<InternalTag>
    | Array<AutocompleteGroupedOption<InternalTag>>;
  listboxProps: object;
  listRef: React.MutableRefObject<VariableSizeList | null>;
  keyboardFocusIndex: number | null;
  filter: string;
  enforceOntologies: boolean;
}) {
  const itemCount = hasNextPage
    ? sortedOptions.length + 1
    : sortedOptions.length;
  const loadMoreItems = isNextPageLoading ? () => {} : loadNextPage;
  const isItemLoaded = (index: number) =>
    !hasNextPage || index < sortedOptions.length;

  const Item = ({
    index,
    style,
  }: {
    index: number;
    style: React.CSSProperties;
  }) => {
    const { classes } = useStyles({ index, keyboardFocusIndex });
    if (!isItemLoaded(index) && isNextPageLoading) {
      return <li style={style}>Loading...</li>;
    }
    if (!groupedOptions || index >= groupedOptions.length)
      return <li style={style} />;

    const option = groupedOptions[index] as InternalTag;
    const name = option.value || "no name";
    const tagIsAllowed = isAllowed(
      checkInternalTag(option, { enforceOntologies })
    );

    const start = name.indexOf(filter);
    const end = start + filter.length;
    const label =
      start > -1 ? (
        <>
          {name.substring(0, start)}
          <b>{filter}</b>
          {name.substring(end)}
        </>
      ) : (
        name
      );

    return (
      <StyledMenuItem
        {...getOptionProps({ option, index })}
        className={classes.menuItem}
        style={{
          /*
           * This style object is what positions the MenuItem correctly within
           * the virtualised list; it gives it `position: absolute` with a top,
           * left, height (as specified by `itemSize`), and width
           */
          ...style,

          /*
           * These styles, which use `option` to conditionally determine the
           * style value, must be here and not in the `makeStyles` above
           * because otherwise an indexing error occurs when the user presses
           * backspace inside the filter text field.
           */
          filter: tagIsAllowed ? "" : "opacity(0.2)",
          pointerEvents: tagIsAllowed ? "auto" : "none",

          /*
           * Scroll horizontally rather than wrap. The styles are here rather
           * than in `makeStyles` above because the width will be overriden by
           * the `style` variable coming from `InfiniteLoader` if it is
           * specified in a class.
           */
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
         * At first glance, it may seem sensible to provide checkboxes for the
         * user to be able to deselect tags from within the popup window of
         * suggested tags. However, in some places in the product tags are
         * persisted in the database as a simple array or comma-separated
         * string so when the list of suggestions contains multiple tags with
         * the same name string we run into issues, for example when when
         * there are different versions of the same ontology file in play. It
         * is impossible for us to identify which partiular tag from which
         * version the user original chose, so we can't display a checked
         * checkbox against just that one suggestion. Instead, what we can do
         * is disable all suggestions whose name matches any of the already
         * selected tags to prevent the user from picking the same tag string
         * twice. Removing tags from the selection is therefore the
         * responsibility of the parent component. If we were to store all of
         * the metadata in the database in everywhere where tags are used then
         * we could look at add checkboxes to the combobox.
         */}
        <ListItemText
          primary={label}
          secondary={helpText(checkInternalTag(option, { enforceOntologies }))}
        />
      </StyledMenuItem>
    );
  };

  return (
    <InfiniteLoader
      isItemLoaded={isItemLoaded}
      itemCount={itemCount}
      loadMoreItems={loadMoreItems}
    >
      {({ onItemsRendered, ref }) => (
        <List
          {...listboxProps}
          height={300}
          itemCount={itemCount}
          onItemsRendered={onItemsRendered}
          width={POPOVER_WIDTH}
          innerElementType="ul"
          /*
           * Here, we're merging the two refs. `ref` comes from InfiniteLoader,
           * and `listRef` comes from the parent component where it is used to
           * call `resetAfterIndex` when `itemSize` should be recalculated. For
           * more information on exactly how this works see this StackOverflow
           * answer https://stackoverflow.com/a/70284705
           */
          ref={(node: VariableSizeList) => {
            ref(node);
            listRef.current = node;
          }}
          /*
           * This calculates the height that should be allocated for any given
           * tag, as identified by its index. Most tags will be allocated an
           * OPTION_HEIGHT amount of space but some show helper text underneath
           * and so need more space.
           *
           * Space cannot be allocated dynamically with CSS, e.g. with flexbox,
           * because the virtualised list library code needs to know all of the
           * vertical offsets to calculate what is visible and thus what should
           * be rendered.
           *
           * `itemSize` is not automatically recalculated when the underlying
           * list of `tags` changes (would be ideal if it integrated with mobx)
           * instead `listRef.current.resetAfterIndex(0)` should be called
           * whenever the heights should be recalculated.
           *
           * `i`, the index of a given tag, could be greater than `tags.length`
           * when the "Loading..." placeholder tag is showing. As such, we're
           * using `getAt` and `orElse` to avoid dealing with undefined.
           */
          itemSize={(i) =>
            OPTION_HEIGHT +
            ArrayUtils.getAt(i, sortedOptions)
              .map((tag) => {
                const tagHasHelpText =
                  helpText(checkInternalTag(tag, { enforceOntologies })) ===
                  null;
                return tagHasHelpText ? 0 : 20;
              })
              .orElse(0)
          }
        >
          {Item}
        </List>
      )}
    </InfiniteLoader>
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
      }
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
      }
>({
  onSelection,
  value,
  anchorEl,
  onClose,
  enforceOntologies,
}: TagsComboboxArgs<Toggle>): React.ReactNode {
  const [tags, setTags] = useState<Array<InternalTag>>([]);
  const [isNextPageLoading, setIsNextPageLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState("");
  const [reachedEnd, setReachedEnd] = useState(false);
  const [error, setError] = useState(false);
  const [keyboardFocusIndex, setKeyboardFocusIndex] = useState<number | null>(
    null
  );
  const listRef = useRef<VariableSizeList | null>(null);

  const loadPage = (): Promise<{
    tags: Array<InternalTag>;
    lastPage: boolean;
  }> => {
    if (reachedEnd) return Promise.resolve({ tags: [], lastPage: true });
    setIsNextPageLoading(true);
    setError(false);
    return fetch(
      `/workspace/editor/structuredDocument/userTagsAndOntologies?pos=${page}&tagFilter=${filter}`
    )
      .then((response) => response.json())
      .then(({ data }: { data: Array<string> }) => {
        const alreadySelectedTagValues = value.map((v) => v.value);
        return {
          lastPage:
            data.includes(SMALL_DATASET_SIGNAL) ||
            data.includes(FINAL_DATA_SIGNAL),
          tags: parseEncodedTags(data).map(
            (tag: Tag): InternalTag => ({
              ...tag,
              selected: alreadySelectedTagValues.has(tag.value),
            })
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
    return stableSort(tags, (tagA, tagB) => {
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
  const {
    getRootProps,
    getInputProps,
    getListboxProps,
    getOptionProps,
    groupedOptions,
  } = useAutocomplete({
    open: Boolean(anchorEl),
    options: sortedOptions,
    getOptionLabel: (option) => {
      // this can happen when user types in a filter
      if (typeof option === "string") return option;

      return option.value;
    },
    filterOptions: (x) => x,
    onInputChange: (event, newInputValue, reason) => {
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
    onClose: (
      event: React.SyntheticEvent<Element, Event>,
      reason: AutocompleteCloseReason
    ) => {
      /*
       * This event is fired whenever the user taps inside the Popover. There
       * are various different parts of the Popover that the user may tap on
       * and we want to choose just those for which a tag option -- i.e. an
       * <li> tag -- is the target.
       */
      let li = null;
      const relatedTarget = (event as React.FocusEvent).relatedTarget;
      if (
        event.currentTarget.nodeName === "INPUT" &&
        relatedTarget?.nodeName === "LI"
      ) {
        li = relatedTarget;
      }
      if (event.currentTarget.nodeName === "LI") {
        li = event.currentTarget;
      }

      if ((reason === "blur" || reason === "selectOption") && li) {
        const { tagValue, tagVocabulary, tagUri, tagVersion } = (
          li as HTMLLIElement
        ).dataset;
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
            vocabulary:
              tagVocabulary === ""
                ? Optional.empty()
                : Optional.present(tagVocabulary as string),
            uri:
              tagUri === ""
                ? Optional.empty()
                : Optional.present(tagUri as string),
            version:
              tagVersion === ""
                ? Optional.empty()
                : Optional.present(tagVersion as string),
          });
        }
        onClose();
      }
    },
  });

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
      }))
    );
    listRef.current?.resetAfterIndex(0);
  }, [value]);

  const [debounceTimeout, setDebounceTimeout] = useState<NodeJS.Timeout | null>(
    null
  );
  function debounce<FuncReturn>(
    func: () => FuncReturn,
    timeout: number = 1000
  ): () => void {
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
        listRef.current?.resetAfterIndex(0);
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
        (
          getInputProps().ref as React.RefObject<HTMLInputElement>
        ).current?.focus();
      }, 0);
    }
  }, [anchorEl]);

  const textFieldId = useId();
  return (
    <>
      <Popover
        onClose={() => {
          onClose();
          setKeyboardFocusIndex(null);
        }}
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
        PaperProps={{
          variant: "outlined",
          style: {
            padding: "4px",
            paddingBottom: "12px",
            width: POPOVER_WIDTH,
          },
        }}
        BackdropProps={{
          invisible: false,
          transitionDuration: window.matchMedia(
            "(prefers-reduced-motion: reduce)"
          ).matches
            ? 0
            : 225,
        }}
        keepMounted
        transitionDuration={
          window.matchMedia("(prefers-reduced-motion: reduce)").matches
            ? 0
            : "auto"
        }
      >
        <div
          {...getRootProps()}
          style={{
            padding: "8px",
            ...getRootProps().style,
          }}
        >
          <TextField
            InputLabelProps={{
              htmlFor: textFieldId,
            }}
            variant="standard"
            label="Filter suggested tags"
            InputProps={{
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
                      <FontAwesomeIcon
                        icon="spinner"
                        spin
                        size="sm"
                        style={{ animationDuration: "1.5s" }}
                      />
                    </div>
                  </Grow>
                </InputAdornment>
              ),
            }}
            inputProps={{
              ...getInputProps(),
              id: textFieldId,
              value: filter,
            }}
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
                      chosenTag.version
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
                      })
                    )
                  ) {
                    newIndex = keyboardFocusIndex ?? 0;
                    break;
                  }
                } while (
                  !isAllowed(
                    checkInternalTag(sortedOptions[newIndex], {
                      enforceOntologies,
                    })
                  )
                );
                setKeyboardFocusIndex(newIndex);
                listRef.current?.scrollToItem(newIndex);
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
                      })
                    )
                  ) {
                    newIndex = keyboardFocusIndex ?? 0;
                    break;
                  }
                } while (
                  !isAllowed(
                    checkInternalTag(sortedOptions[newIndex], {
                      enforceOntologies,
                    })
                  )
                );
                setKeyboardFocusIndex(newIndex);
                listRef.current?.scrollToItem(newIndex);
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
            style={{
              fontSize: "1.1em",
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
            <AlertTitle>
              No matching tag suggestions{" "}
              {enforceOntologies ? "from ontologies" : ""}.
            </AlertTitle>
            {enforceOntologies ? <></> : <>To use a new tag, press Enter.</>}
          </Alert>
        )}
        {error && (
          <Alert severity="warning">
            <AlertTitle>Error fetching tags</AlertTitle>
            {enforceOntologies ? (
              <>
                Please check that the ontology files are correctly configured.
              </>
            ) : (
              <>Simply type in the tag and press enter instead.</>
            )}
          </Alert>
        )}
      </Popover>
    </>
  );
}
