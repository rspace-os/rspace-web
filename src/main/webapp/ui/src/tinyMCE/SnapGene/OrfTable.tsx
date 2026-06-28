import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import LoadingCircular from "../../components/LoadingCircular";
import { getSorting, paginationOptions } from "../../util/table";
import type { Order } from "../../util/types";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function OrfTable(props: any) {
  const { t } = useTranslation("apps");
  const readingFrameOptions = {
    ALL: { label: t("tinyMce.snapGene.readingFrames.all"), filter: [-3, -2, -1, 1, 2, 3] },
    FORWARD: { label: t("tinyMce.snapGene.readingFrames.forward"), filter: [1, 2, 3] },
    REVERSE: { label: t("tinyMce.snapGene.readingFrames.reverse"), filter: [-1, -2, -3] },
    FIRST_FORWARD: { label: t("tinyMce.snapGene.readingFrames.firstForward"), filter: [1] },
    FIRST_REVERSE: { label: t("tinyMce.snapGene.readingFrames.firstReverse"), filter: [-1] },
  };
  const headCells = [
    {
      id: "fullRangeBegin",
      numeric: false,
      disablePadding: false,
      label: t("tinyMce.snapGene.columns.fullRangeBegin"),
    },
    { id: "fullRangeEnd", numeric: false, disablePadding: false, label: t("tinyMce.snapGene.columns.fullRangeEnd") },
    {
      id: "molecularWeight",
      numeric: false,
      disablePadding: false,
      label: t("tinyMce.snapGene.columns.molecularWeight"),
    },
    { id: "readingFrame", numeric: false, disablePadding: false, label: t("tinyMce.snapGene.columns.readingFrame") },
    { id: "translation", numeric: false, disablePadding: false, label: t("tinyMce.snapGene.columns.translation") },
  ];
  const [order, setOrder] = React.useState<Order>("desc");
  const [orderBy, setOrderBy] = React.useState("version");
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(10);
  const [loading, setLoading] = React.useState(true);

  const [readingFrameOption, setReadingFrameOption] = React.useState("ALL");
  const [oldReadingFrameOption, setOldReadingFrameOption] = React.useState("ALL");
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [results, setResults] = React.useState<Array<any>>([]);
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [filteredResults, setFilteredResults] = React.useState<Array<any>>([]);

  const fetchData = () => {
    setLoading(true);

    const url = `/molbiol/dna/orfs/${props.id}`;
    axios
      .get(url)
      .then((response) => {
        setResults(response.data.ORFs);
        filterResults(response.data.ORFs);
      })
      .catch((error) => {
        RS.confirm(error.response.data, "warning", "infinite");
      })
      .finally(() => {
        setLoading(false);
      });
  };

  // fetch data on load
  useEffect(() => {
    fetchData();
  }, []);

  // filter results on Apply
  useEffect(() => {
    setPage(0);
    setOldReadingFrameOption(readingFrameOption);
    filterResults();
  }, [props.clicked]);

  useEffect(() => {
    updateDisabled();
  }, [readingFrameOption, oldReadingFrameOption]);

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const filterResults = (passed_results?: Array<any>) => {
    const used_results = passed_results || results; // in case the results are not set yet
    const to_include = readingFrameOptions[readingFrameOption as keyof typeof readingFrameOptions].filter;
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    const filtered = used_results.filter((r: any) => to_include.includes(r.readingFrame));
    setFilteredResults(filtered);
  };

  const handleChange = (value: string) => {
    setReadingFrameOption(value);
  };

  const handleRequestSort = (_event: React.MouseEvent<HTMLSpanElement>, property: string) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const handleChangePage = (_event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const updateDisabled = () => {
    props.setDisabled(readingFrameOption === oldReadingFrameOption);
  };

  const emptyRows = rowsPerPage - Math.min(rowsPerPage, filteredResults.length - page * rowsPerPage);

  return (
    <>
      <Grid size={8}>
        {loading && <LoadingCircular />}
        {!loading && (
          <>
            <TableContainer sx={{ maxHeight: "449px" }}>
              <Table stickyHeader size="small" aria-label={t("tinyMce.snapGene.orfTable")}>
                <EnhancedTableHead
                  headCells={headCells}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                  rowCount={filteredResults.length}
                />
                <TableBody>
                  {filteredResults
                    .toSorted(getSorting(order, orderBy))
                    .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                    .map((result: any) => (
                      <TableRow hover tabIndex={-1} key={result.id}>
                        <TableCell align="left">{result.fullRangeBegin}</TableCell>
                        <TableCell align="left">{result.fullRangeEnd}</TableCell>
                        <TableCell align="left">{result.molecularWeight}</TableCell>
                        <TableCell align="left">{result.readingFrame}</TableCell>
                        <TableCell align="left">{result.translation}</TableCell>
                      </TableRow>
                    ))}
                  {emptyRows > 0 && (
                    <TableRow sx={{ height: 33 * emptyRows }}>
                      <TableCell colSpan={6} />
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              rowsPerPageOptions={paginationOptions(filteredResults.length)}
              component="div"
              count={filteredResults.length}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={handleChangePage}
              onRowsPerPageChange={handleChangeRowsPerPage}
            />
          </>
        )}
      </Grid>
      <Grid sx={{ textAlign: "right" }} size={2}>
        <FormControl component="fieldset" sx={{ mb: "30px" }}>
          <FormLabel component="legend" sx={{ mb: "10px" }}>
            {t("tinyMce.snapGene.openReadingFrames")}
          </FormLabel>
          <RadioGroup
            aria-label={t("tinyMce.snapGene.readingFrames.aria")}
            name="enzymeSet"
            value={readingFrameOption}
            onChange={(event) => handleChange(event.target.value)}
          >
            {Object.keys(readingFrameOptions).map((key) => (
              <FormControlLabel
                value={key}
                key={key}
                control={<Radio color="primary" />}
                label={readingFrameOptions[key as keyof typeof readingFrameOptions].label}
                labelPlacement="start"
              />
            ))}
          </RadioGroup>
        </FormControl>
      </Grid>
    </>
  );
}
