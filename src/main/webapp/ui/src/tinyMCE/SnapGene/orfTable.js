"use strict";
import React, { useEffect } from "react";
import { makeStyles } from "tss-react/mui";
import Grid from "@mui/material/Grid";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import axios from "@/common/axios";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import LoadingCircular from "../../components/LoadingCircular";
import { stableSort, getSorting, paginationOptions } from "../../util/table";

const useStyles = makeStyles()((theme) => ({
  settings: {
    textAlign: "right",
  },
  label: {
    marginBottom: "10px",
  },
  radio: {
    marginBottom: "30px",
  },
}));

const readingFrameOptions = {
  ALL: { label: "All", filter: [-3, -2, -1, 1, 2, 3] },
  FORWARD: { label: "Forward", filter: [1, 2, 3] },
  REVERSE: { label: "Reverse", filter: [-1, -2, -3] },
  FIRST_FORWARD: { label: "First forward", filter: [1] },
  FIRST_REVERSE: { label: "First reverse", filter: [-1] },
};

const headCells = [
  {
    id: "fullRangeBegin",
    numeric: false,
    disablePadding: false,
    label: "Full Range Begin",
  },
  {
    id: "fullRangeEnd",
    numeric: false,
    disablePadding: false,
    label: "Full Range End",
  },
  {
    id: "molecularWeight",
    numeric: false,
    disablePadding: false,
    label: "Molecular Weight",
  },
  {
    id: "readingFrame",
    numeric: false,
    disablePadding: false,
    label: "Reading Frame",
  },
  {
    id: "translation",
    numeric: false,
    disablePadding: false,
    label: "Translation",
  },
];

export default function orfTable(props) {
  const { classes } = useStyles();
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("version");
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(10);
  const [loading, setLoading] = React.useState(true);

  const [readingFrameOption, setReadingFrameOption] = React.useState("ALL");
  const [oldReadingFrameOption, setOldReadingFrameOption] =
    React.useState("ALL");
  const [results, setResults] = React.useState([]);
  const [filteredResults, setFilteredResults] = React.useState([]);

  const fetchData = () => {
    setLoading(true);

    let url = `/molbiol/dna/orfs/${props.id}`;
    axios
      .get(url)
      .then((response) => {
        setResults(response.data.ORFs);
        filterResults(response.data.ORFs);
      })
      .catch((error) => {
        RS.confirm(error.response.data, "warning", "infinite");
      })
      .finally(function () {
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

  const filterResults = (passed_results) => {
    passed_results = passed_results || results; // in case the results are not set yet
    const to_include = readingFrameOptions[readingFrameOption].filter;
    const filtered = passed_results.filter((r) =>
      to_include.includes(r.readingFrame)
    );
    setFilteredResults(filtered);
  };

  const handleChange = (value) => {
    setReadingFrameOption(value);
  };

  const handleRequestSort = (event, property) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const updateDisabled = () => {
    props.setDisabled(readingFrameOption == oldReadingFrameOption);
  };

  const emptyRows =
    rowsPerPage -
    Math.min(rowsPerPage, filteredResults.length - page * rowsPerPage);

  return (
    <>
      <Grid item xs={8}>
        {loading && <LoadingCircular />}
        {!loading && (
          <>
            <TableContainer style={{ maxHeight: "449px" }}>
              <Table
                stickyHeader
                className={classes.table}
                aria-labelledby="ORF table"
                size="small"
                aria-label="enhanced table"
              >
                <EnhancedTableHead
                  headCells={headCells}
                  classes={classes}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                  rowCount={filteredResults.length}
                />
                <TableBody>
                  {stableSort(filteredResults, getSorting(order, orderBy))
                    .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                    .map((result) => (
                      <TableRow hover tabIndex={-1} key={result.id}>
                        <TableCell align="left">
                          {result.fullRangeBegin}
                        </TableCell>
                        <TableCell align="left">
                          {result.fullRangeEnd}
                        </TableCell>
                        <TableCell align="left">
                          {result.molecularWeight}
                        </TableCell>
                        <TableCell align="left">
                          {result.readingFrame}
                        </TableCell>
                        <TableCell align="left">{result.translation}</TableCell>
                      </TableRow>
                    ))}
                  {emptyRows > 0 && (
                    <TableRow style={{ height: 33 * emptyRows }}>
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

      <Grid item xs={2} className={classes.settings}>
        <FormControl component="fieldset" className={classes.radio}>
          <FormLabel component="legend" className={classes.label}>
            Open Reading Frames
          </FormLabel>
          <RadioGroup
            aria-label="Enzyme type"
            name="enzymeSet"
            value={readingFrameOption}
            onChange={(event) => handleChange(event.target.value)}
          >
            {Object.keys(readingFrameOptions).map((key) => (
              <FormControlLabel
                value={key}
                key={key}
                control={<Radio color="primary" />}
                label={readingFrameOptions[key].label}
                labelPlacement="start"
              />
            ))}
          </RadioGroup>
        </FormControl>
      </Grid>
    </>
  );
}
