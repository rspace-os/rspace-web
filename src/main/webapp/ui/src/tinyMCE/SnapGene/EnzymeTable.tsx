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
import axios from "@/common/axios";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import LoadingCircular from "../../components/LoadingCircular";
import { getSorting, paginationOptions, stableSort } from "../../util/table";
import type { Order } from "../../util/types";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
declare const RS: any;

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

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
export default function EnzymeTable(props: any) {
  const [order, setOrder] = React.useState<Order>("desc");
  const [orderBy, setOrderBy] = React.useState("enzyme");
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(10);
  const [loading, setLoading] = React.useState(true);
  const [enzymeSet, setEnzymeSet] = React.useState("UNIQUE_SIX_PLUS");
  const [oldEnzymeSet, setOldEnzymeSet] = React.useState("UNIQUE_SIX_PLUS");
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const [enzymeList, setEnzymeList] = React.useState<Array<any>>([]);

  const fetchEnzymes = () => {
    setLoading(true);

    const url = `/molbiol/dna/enzymes/${props.id}?enzymeSet=${enzymeSet}`;
    axios
      .get(url)
      .then((response) => {
        generateEnzymeList(response.data.enzymes);
      })
      .catch((error) => {
        RS.confirm(error.response.data, "warning", "infinite");
      })
      .finally(() => {
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

  const handleChange = (value: string) => {
    setEnzymeSet(value);
  };

  // biome-ignore lint/correctness/noUnusedFunctionParameters: initial biome migration
  const handleRequestSort = (event: React.MouseEvent<HTMLSpanElement>, property: string) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  // biome-ignore lint/correctness/noUnusedFunctionParameters: initial biome migration
  const handleChangePage = (event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const generateEnzymeList = (list: Array<any>) => {
    // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
    const new_list = list.map((enzyme: any) => {
      // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
      return enzyme.hits.map((hit: any) => {
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
    // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
    props.setDisabled(enzymeSet == oldEnzymeSet);
  };

  const emptyRows = rowsPerPage - Math.min(rowsPerPage, enzymeList.length - page * rowsPerPage);

  return (
    <>
      <Grid size={8}>
        {loading && <LoadingCircular />}
        {!loading && (
          <>
            <TableContainer sx={{ maxHeight: "387px" }}>
              <Table stickyHeader size="small" aria-label="Enzyme table">
                <EnhancedTableHead
                  headCells={headCells}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                  rowCount={enzymeList.length}
                />
                <TableBody>
                  {stableSort(enzymeList, getSorting(order, orderBy))
                    .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                    // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
                    .map((enzyme: any) => (
                      <TableRow
                        hover
                        tabIndex={-1}
                        key={`${enzyme.name}-${enzyme.id}-${enzyme.topCutPosition}-${enzyme.bottomCutPosition}`}
                      >
                        <TableCell align="left">{enzyme.name}</TableCell>
                        <TableCell align="right">{enzyme.bottomCutPosition}</TableCell>
                        <TableCell align="right">{enzyme.topCutPosition}</TableCell>
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
      <Grid sx={{ textAlign: "right" }} size={2}>
        <FormControl component="fieldset" sx={{ mb: "30px" }}>
          <FormLabel component="legend" sx={{ mb: "10px" }}>
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
                label={enzymeSetOptions[key as keyof typeof enzymeSetOptions]}
                labelPlacement="start"
              />
            ))}
          </RadioGroup>
        </FormControl>
      </Grid>
    </>
  );
}
