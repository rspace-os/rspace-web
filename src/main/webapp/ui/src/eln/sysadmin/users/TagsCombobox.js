//@flow

import React, {
  type Node,
  useEffect,
  useState,
  useRef,
  useMemo,
  useId,
  type ElementRef,
} from "react";
import TextField from "@mui/material/TextField";
import useAutocomplete from "@mui/material/useAutocomplete";
import { VariableSizeList as List } from "react-window";
import InfiniteLoader from "react-window-infinite-loader";
import Popover from "@mui/material/Popover";
import InputAdornment from "@mui/material/InputAdornment";
import { StyledMenuItem } from "../../../components/StyledMenu";
import FilterIcon from "@mui/icons-material/FilterAlt";
import ListItemText from "@mui/material/ListItemText";
import {
  checkUserInputString,
  helpText,
  isAllowed,
} from "../../../components/Tags/TagValidation";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { makeStyles } from "tss-react/mui";
import { stableSort } from "../../../util/table";
import RsSet from "../../../util/set";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons";
library.add(faSpinner);
import Grow from "@mui/material/Grow";
import axios from "@/common/axios";

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
 * Whilst the code is setup for infinite loading, currently the endpoint
 * returns a list of all of the tags.
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

type InternalTag = {|
  value: string,
  selected: boolean,
|};

/*
 * makeStyles is used because withStyles is not performant enough to render the
 * menu items as the user scrolls the virtualised list
 */
const useStyles = makeStyles()((theme, { index, keyboardFocusIndex }) => ({
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
  sortedOptions,
  getOptionProps,
  groupedOptions,
  listboxProps,
  listRef,
  keyboardFocusIndex,
  filter,
}: {
  sortedOptions: Array<InternalTag>,
  getOptionProps: ({ option: InternalTag, index: number, ... }) => { ... },
  groupedOptions: Array<InternalTag>,
  listboxProps: { ... },
  listRef: ElementRef<typeof List>,
  keyboardFocusIndex: ?number,
  filter: string,
}) {
  const Item = ({ index, style }: { index: number, style: mixed }) => {
    const { classes } = useStyles({ index, keyboardFocusIndex });
    if (!groupedOptions || index >= groupedOptions.length)
      return <li style={style} />;

    const option = groupedOptions[index];
    const name = option.value || "no name";
    const tagIsAllowed = !option.selected;

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
        aria-selected={index === keyboardFocusIndex}
        aria-disabled={!tagIsAllowed}
      >
        <ListItemText primary={label} secondary={""} />
      </StyledMenuItem>
    );
  };

  return (
    <InfiniteLoader
      isItemLoaded={() => true}
      itemCount={sortedOptions.length}
      loadMoreItems={() => {}}
    >
      {({ onItemsRendered, ref }) => (
        <List
          {...listboxProps}
          height={300}
          itemCount={sortedOptions.length}
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
          ref={(node) => {
            ref.current = node;
            listRef.current = node;
          }}
          itemSize={() => OPTION_HEIGHT}
        >
          {Item}
        </List>
      )}
    </InfiniteLoader>
  );
}

type TagsComboboxArgs = {|
  /*
   * This is the set of currently selected tags. It is a Set because this UI
   * component does not care about the order of the tags that the parent
   * component may or may not persist. This parameter is solely used to prevent
   * the user from choosing a tag that they have already selected.
   */
  value: RsSet<string>,

  /*
   * Identifies if the popup should be open and if so where it should be
   * positioned. If null then the popup is not shown. If an element then the
   * popup is shown adjacent to that element.
   */
  anchorEl: ?Element,

  /*
   * When the user chooses a tag from the suggestions, or enters a new one of
   * their own, this event handler is called. There is no mechanism for
   * removing tags from the selection within this component and providing such
   * functionality is the responsiblity of the parent component. It is expected
   * that the parent component should update the `value` prop above in this
   * callback by appending this new tag.
   */
  onSelection: (string) => void,

  /*
   * If the user chooses to close the popup, either with or without making a
   * selection, this event handler is called. It is expected that the parent
   * component will set the `anchorEl` prop to null as it is this that will
   * actually close the popup.
   */
  onClose: () => void,
|};

export default function TagsCombobox({
  onSelection,
  value,
  anchorEl,
  onClose,
}: TagsComboboxArgs): Node {
  const [tags, setTags] = useState<Array<InternalTag>>([]);
  const [isNextPageLoading, setIsNextPageLoading] = useState(false);
  const [filter, setFilter] = useState("");
  const [error, setError] = useState(false);
  const [keyboardFocusIndex, setKeyboardFocusIndex] = useState<number | null>(
    null
  );
  const listRef = useRef();

  const loadPage = (): Promise<{|
    tags: Array<InternalTag>,
    lastPage: boolean,
  |}> => {
    if (filter.length < 2) return Promise.resolve({ tags: [], lastPage: true });
    setIsNextPageLoading(true);
    setError(false);
    return axios
      .get<Array<string>>(`/system/users/allUserTags?tagFilter=${filter}`)
      .then(({ data }) => {
        return {
          lastPage: true,
          tags: data.map((tag: string): InternalTag => ({
            value: tag,
            selected: value.has(tag),
          })),
        };
      })
      .catch(() => {
        setError(true);
        return {
          tags: ([]: Array<InternalTag>),
          lastPage: true,
        };
      })
      .finally(() => {
        setIsNextPageLoading(false);
      });
  };

  const sortedOptions = useMemo(() => {
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
      event: {
        relatedTarget: ?{
          dataset: {
            tagValue: string,
          },
          nodeName: string,
        },
        currentTarget: {
          dataset: {
            tagValue: string,
          },
          nodeName: string,
        },
        ...
      },
      reason: "toggleInput" | "escape" | "selectOption" | "blur"
    ) => {
      /*
       * This event is fired whenever the user taps inside the Popover. There
       * are various different parts of the Popover that the user may tap on
       * and we want to choose just those for which a tag option -- i.e. an
       * <li> tag -- is the target.
       */
      let li = null;
      if (
        event.currentTarget.nodeName === "INPUT" &&
        event.relatedTarget?.nodeName === "LI"
      ) {
        li = event.relatedTarget;
      }
      if (event.currentTarget.nodeName === "LI") {
        li = event.currentTarget;
      }

      if ((reason === "blur" || reason === "selectOption") && li) {
        const { tagValue } = li.dataset;
        onSelection(tagValue);
        onClose();
      }
    },
  });

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
      }))
    );
    listRef.current?.resetAfterIndex(0);
  }, [value]);

  const [debounceTimeout, setDebounceTimeout] = useState<?TimeoutID>(null);
  function debounce<FuncReturn>(
    func: () => FuncReturn,
    timeout: number = 1000
  ): () => void {
    return () => {
      clearTimeout(debounceTimeout);
      setDebounceTimeout(setTimeout(() => func(), timeout));
    };
  }

  useEffect(() => {
    debounce(() => {
      // this assumes that the code that called `setFilter` also reset `page` to 0
      void loadPage().then(({ lastPage, tags: newPageOfTags }) => {
        setTags(newPageOfTags);
        listRef.current?.resetAfterIndex(0);
      });
    })();
  }, [filter]);

  useEffect(() => {
    void loadPage().then(({ tags }) => setTags(tags));
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
        getInputProps().ref.current?.focus();
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
                 * enter any tag they like.
                 */
                if (isAllowed(checkUserInputString(filter))) {
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
                  if (
                    newIndex === tags.length - 1 &&
                    sortedOptions[newIndex].selected
                  ) {
                    newIndex = keyboardFocusIndex ?? 0;
                    break;
                  }
                } while (sortedOptions[newIndex].selected);
                setKeyboardFocusIndex(newIndex);
                listRef.current?.scrollToItem(newIndex);
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
            hasNextPage={false}
            isNextPageLoading={isNextPageLoading}
            sortedOptions={sortedOptions}
            loadNextPage={() => {}}
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
            <AlertTitle>No matching tag suggestions </AlertTitle>
            <>To use a new tag, press Enter.</>
          </Alert>
        )}
        {error && (
          <Alert severity="warning">
            <AlertTitle>Error fetching tags</AlertTitle>
            <>Simply type in the tag and press enter instead.</>
          </Alert>
        )}
      </Popover>
    </>
  );
}
