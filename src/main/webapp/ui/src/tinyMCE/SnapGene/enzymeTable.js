"use strict";
import React, { useEffect } from "react";
import { makeStyles } from "tss-react/mui";
import Grid from "@mui/material/Grid";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import axios from "axios";
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

const enzymeSetOptions = {
  UNIQUE_SIX_PLUS: "Unique six plus",
  UNIQUE: "Unique",
  SIX_PLUS: "Six plus",
  UNIQUE_AND_DUAL: "Unique and dual",
  COMMERCIAL_NONREDUNDANT: "Commercial nonredundant",
};

const headCells = [
  { id: "name", numeric: false, disablePadding: false, label: "Enzyme" },
  {
    id: "bottomCutPosition",
    numeric: true,
    disablePadding: false,
    label: "Bottom cut position",
  },
  {
    id: "topCutPosition",
    numeric: true,
    disablePadding: false,
    label: "Top cut position",
  },
];

export default function EnzymeTable(props) {
  const { classes } = useStyles();
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("enzyme");
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(10);
  const [loading, setLoading] = React.useState(true);
  const [enzymeSet, setEnzymeSet] = React.useState("UNIQUE_SIX_PLUS");
  const [oldEnzymeSet, setOldEnzymeSet] = React.useState("UNIQUE_SIX_PLUS");
  const [enzymeList, setEnzymeList] = React.useState([]);

  const fetchEnzymes = () => {
    setLoading(true);

    let url = `/molbiol/dna/enzymes/${props.id}?enzymeSet=${enzymeSet}`;
    axios
      .get(url)
      .then((response) => {
        generateEnzymeList(response.data.enzymes);
      })
      .catch((error) => {
        RS.confirm(error.response.data, "warning", "infinite");
      })
      .finally(function () {
        setLoading(false);
      });
  };

  // handle apply
  useEffect(() => {
    setPage(0);
    setEnzymeList([]);
    setOldEnzymeSet(enzymeSet);
    fetchEnzymes();
  }, [props.clicked]);

  useEffect(() => {
    updateDisabled();
  }, [enzymeSet, oldEnzymeSet]);

  const handleChange = (value) => {
    setEnzymeSet(value);
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

  const generateEnzymeList = (list) => {
    let new_list = list.map((enzyme) => {
      return enzyme.hits.map((hit) => {
        return {
          name: enzyme.name,
          id: enzyme.id,
          topCutPosition: hit.topCutPosition,
          bottomCutPosition: hit.bottomCutPosition,
        };
      });
    });
    setEnzymeList(new_list.flat());
  };

  const updateDisabled = () => {
    props.setDisabled(enzymeSet == oldEnzymeSet);
  };

  const emptyRows =
    rowsPerPage - Math.min(rowsPerPage, enzymeList.length - page * rowsPerPage);

  return (
    <>
      <Grid item xs={8}>
        {loading && <LoadingCircular />}
        {!loading && (
          <>
            <TableContainer style={{ maxHeight: "387px" }}>
              <Table
                stickyHeader
                className={classes.table}
                aria-labelledby="Enzyme table"
                size="small"
                aria-label="enhanced table"
              >
                <EnhancedTableHead
                  headCells={headCells}
                  classes={classes}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                  rowCount={enzymeList.length}
                />
                <TableBody>
                  {stableSort(enzymeList, getSorting(order, orderBy))
                    .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                    .map((enzyme) => (
                      <TableRow
                        hover
                        tabIndex={-1}
                        key={`${enzyme.name}-${enzyme.id}-${enzyme.topCutPosition}-${enzyme.bottomCutPosition}`}
                      >
                        <TableCell align="left">{enzyme.name}</TableCell>
                        <TableCell align="right">
                          {enzyme.bottomCutPosition}
                        </TableCell>
                        <TableCell align="right">
                          {enzyme.topCutPosition}
                        </TableCell>
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
              rowsPerPageOptions={paginationOptions(enzymeList.length)}
              component="div"
              count={enzymeList.length}
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
            Enzyme Sets
          </FormLabel>
          <RadioGroup
            aria-label="Enzyme type"
            name="enzymeSet"
            value={enzymeSet}
            onChange={(event) => handleChange(event.target.value)}
          >
            {Object.keys(enzymeSetOptions).map((key) => (
              <FormControlLabel
                value={key}
                key={key}
                control={<Radio color="primary" />}
                label={enzymeSetOptions[key]}
                labelPlacement="start"
              />
            ))}
          </RadioGroup>
        </FormControl>
      </Grid>
    </>
  );
}
