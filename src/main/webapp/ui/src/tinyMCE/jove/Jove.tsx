import React, { useState, useEffect, useMemo } from "react";
import { Grid, CircularProgress } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { search, type Article, type ArticleId } from "./JoveClient";
import ErrorView from "./ErrorView";
import { ErrorReason, Order, SearchParam } from "./Enums";
import useLocalStorage from "../../hooks/browser/useLocalStorage";
import materialTheme from "../../theme";
import ResultsTable from "./ResultsTable";
import JoveSearchBar from "./JoveSearchBar";
import { type Cell } from "../../components/EnhancedTableHead";
import { type Order as OrderType } from "../../util/types";
import { InvalidLocalStorageState } from "../../util/error";
import { mapNullable } from "../../util/Util";

const TABLE_HEADER_CELLS: Array<Cell<"" | "thumbnail" | "title" | "section">> =
  [
    { id: "thumbnail", numeric: false, label: "Thumbnail" },
    { id: "title", numeric: false, label: "Title" },
    { id: "section", numeric: false, label: "Section" },
  ];

const VISIBLE_HEADER_CELLS = TABLE_HEADER_CELLS;
let SELECTED_RESULTS = [] as Array<Article>;
export const getSelectedResults = (): Array<Article> => SELECTED_RESULTS;
export const getHeaders = (): typeof TABLE_HEADER_CELLS => VISIBLE_HEADER_CELLS;
export const getOrder = (): OrderType => {
  const storedJson = localStorage.getItem("joveSearchOrder");
  const order = mapNullable(JSON.parse, storedJson) ?? Order.asc;
  if (order !== "asc" && order !== "desc")
    throw new InvalidLocalStorageState(
      `Expected "asc" or "desc", found ${storedJson ?? "nothing"}`,
    );
  return order;
};
export const getOrderBy = (): keyof Article | "" => {
  const localStorageContents = localStorage.getItem("joveSearchOrderBy");
  const orderBy = mapNullable(JSON.parse, localStorageContents);
  const validKeys: Set<keyof Article | ""> = new Set([
    "id",
    "hasvideo",
    "section",
    "thumbnail",
    "title",
    "url",
    "",
  ]);
  if (orderBy === null || typeof orderBy === "undefined")
    throw new InvalidLocalStorageState("Expected a key of Article, found null");
  if (validKeys.has(orderBy)) return orderBy;
  throw new InvalidLocalStorageState(
    `Expected a key of Article, found ${orderBy}`,
  );
};

export default function Jove(): React.ReactNode {
  const [searchResults, setSearchResults] = useState<Array<Article>>([]);
  const [searchDone, setSearchDone] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchParam, setSearchParam] = useState<
    (typeof SearchParam)[keyof typeof SearchParam]
  >(SearchParam.queryString);
  const [errorReason, setErrorReason] = useState<
    (typeof ErrorReason)[keyof typeof ErrorReason]
  >(ErrorReason.None);
  const [errorMessage, setErrorMessage] = useState("");

  const [selectedJoveIds, setSelectedJoveIds] = useState<Array<ArticleId>>([]);
  const [order, setOrder] = useLocalStorage<OrderType>(
    "joveSearchOrder",
    Order.asc,
  );
  const [orderBy, setOrderBy] = useLocalStorage<
    "" | "thumbnail" | "title" | "section"
  >("joveSearchOrderBy", "");

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useLocalStorage("joveRowsPerPage", 20);
  const [count, setCount] = useState(0);

  const handleChangePage = (newPage: number) => {
    setPage(newPage);
  };

  const handleRowsPerPageChange = (pageSize: number) => {
    setRowsPerPage(pageSize);
    handleChangePage(0);
  };

  const submitSearch = () => {
    console.log("Submit Search");
    if (page !== 0) {
      setPage(0);
    } else {
      void searchJove(searchParam, searchQuery, page, rowsPerPage);
    }
  };

  /**
   * This search function calls JoveClient.js to make apis calls to Jove and set the results.
   * @param searchParam this param is currently not used but can change what the search query is for, i.e query string, author or institution default is query string.
   * @param searchQuery the query string entered by the user
   * @param page current page
   * @param rowsPerPage number of rows per page
   */
  const searchJove = async (
    searchParam: (typeof SearchParam)[keyof typeof SearchParam],
    searchQuery: string,
    page: number,
    rowsPerPage: number,
  ) => {
    setSearchDone(false);
    setSearchResults([]);
    setErrorReason(ErrorReason.None);
    setErrorMessage("");
    try {
      const searchResult = await search(
        searchParam,
        searchQuery,
        page,
        rowsPerPage,
      );
      if (searchResult) {
        // Only populate the search results array with results that have videos.
        setSearchResults(
          searchResult.data.articlelist.filter((article) => article.hasvideo),
        );
        setCount(searchResult.data.countall);
      } else {
        setCount(0);
      }
    } catch (error) {
      console.log("Error: ", error);
      handleRequestError(error as Error);
    } finally {
      setSearchDone(true);
    }
  };

  const handleSearchQueryChange = (event: { target: { value: string } }) => {
    setSearchQuery(event.target.value);
  };

  const handleSearchParamChange = (event: {
    target: { value: (typeof SearchParam)[keyof typeof SearchParam] };
  }) => {
    setSearchParam(event.target.value);
    setPage(0);
  };

  // This use effect here is essentially the callback for page and rowsPerPage
  // if either of those state values change then a new search will be triggered.
  useEffect(() => {
    void searchJove(searchParam, searchQuery, page, rowsPerPage);
    setOrderBy("");
  }, [page, rowsPerPage, searchParam]);

  SELECTED_RESULTS = useMemo(() => {
    const selected_searchResults = searchResults.filter((results) =>
      selectedJoveIds.includes(results.id),
    );

    window.parent.postMessage(
      {
        mceAction: selected_searchResults.length > 0 ? "enable" : "disable",
      },
      "*",
    );

    return selected_searchResults;
  }, [selectedJoveIds]);

  function handleRequestError(error: { message: string }) {
    if (error.message.slice(error.message.length - 3) === "408") {
      setErrorReason(ErrorReason.Timeout);
    } else if (error.message.slice(error.message.length - 3) === "404") {
      setErrorReason(ErrorReason.NotFound);
    } else if (error.message.slice(error.message.length - 3) === "403") {
      setErrorReason(ErrorReason.Unauthorized);
    } else if (error.message.slice(error.message.length - 3) === "400") {
      setErrorReason(ErrorReason.BadRequest);
    } else {
      setErrorReason(ErrorReason.UNKNOWN);
      setErrorMessage(error.message);
    }
  }

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Grid container alignItems="center">
          <Grid item xs={12}>
            <JoveSearchBar
              searchQuery={searchQuery}
              handleSearchQueryChange={handleSearchQueryChange}
              submitSearch={submitSearch}
            />
          </Grid>
        </Grid>
        <Grid container spacing={1}>
          <Grid item xs={12}>
            {errorReason !== ErrorReason.None ? (
              <ErrorView
                errorReason={errorReason}
                errorMessage={errorMessage}
              />
            ) : (
              <ResultsTable
                page={page}
                onPageChange={handleChangePage}
                visibleHeaderCells={TABLE_HEADER_CELLS}
                searchResults={searchResults}
                setSearchResults={setSearchResults}
                selectedJoveIds={selectedJoveIds}
                setSelectedJoveIds={setSelectedJoveIds}
                order={order}
                orderBy={orderBy}
                setOrder={setOrder}
                setOrderBy={setOrderBy}
                onRowsPerPageChange={handleRowsPerPageChange}
                rowsPerPage={rowsPerPage}
                count={count}
              />
            )}
          </Grid>
          <Grid item xs={12} sx={{ align: "center" }}>
            {!searchDone && <CircularProgress />}
          </Grid>
        </Grid>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
