// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { TableContainer } from "@mui/material";
import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TablePagination from "@mui/material/TablePagination";
import TableRow, { tableRowClasses } from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import { getSorting, stableSort } from "../../util/table";
import { Order } from "./Enums";

export default function ResultsTable({
  page,
  // biome-ignore lint/correctness/noUnusedFunctionParameters: initial biome migration
  setPage,
  visibleHeaderCells,
  animals,
  order,
  setOrder,
  orderBy,
  setOrderBy,
  selectedAnimalIds,
  setSelectedAnimalIds,
  onRowsPerPageChange,
  onPageChange,
  rowsPerPage,
  count,
}: {
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  [key: string]: any;
}) {
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function onRowClick(eartag: any) {
    setSelectedAnimalIds(
      selectedAnimalIds.includes(eartag)
        ? // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
          selectedAnimalIds.filter((id: any) => id !== eartag)
        : [...selectedAnimalIds, eartag],
    );
  }

  // biome-ignore lint/correctness/noUnusedFunctionParameters: initial biome migration
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function handleRequestSort(event: any, property: any) {
    const isDesc = orderBy === property && order === Order.desc;
    setOrder(isDesc ? Order.asc : Order.desc);
    setOrderBy(property);
    onPageChange(0);
  }

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function handleChangePage(_: any, newPage: any) {
    onPageChange(newPage);
  }

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function handleChangeRowsPerPage(event: any) {
    const num = parseInt(event.target.value, 10);
    onRowsPerPageChange(num);
  }

  return (
    <>
      <TableContainer sx={{ mb: "40px" }}>
        <Table aria-label="animal search results">
          <EnhancedTableHead
            headSx={{ background: "#F6F6F6" }}
            headCells={visibleHeaderCells}
            order={order}
            orderBy={orderBy}
            onRequestSort={handleRequestSort}
            selectAll={true}
            onSelectAllClick={(event) => {
              if (event.target.checked) {
                // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
                const newSelected = animals.map((animal: any) => animal.eartag_or_id);
                return setSelectedAnimalIds(newSelected);
              }
              setSelectedAnimalIds([]);
            }}
            numSelected={selectedAnimalIds.length}
            rowCount={animals.length}
          />
          <TableBody>
            {/* biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion */}
            {stableSort(animals, getSorting(order, orderBy)).map((animal: any, index: number) => {
              const isItemSelected = selectedAnimalIds.indexOf(animal.eartag_or_id) !== -1;
              const labelId = `animal-search-results-checkbox-${index}`;

              return (
                <TableRow
                  id={labelId}
                  sx={{
                    [`&.${tableRowClasses.selected}`]: {
                      backgroundColor: "#e3f2fd",
                    },
                    [`&.${tableRowClasses.selected}:hover`]: {
                      backgroundColor: "#e3f2fd",
                    },
                  }}
                  hover
                  tabIndex={-1}
                  role="checkbox"
                  onClick={() => onRowClick(animal.eartag_or_id)}
                  aria-checked={isItemSelected}
                  selected={isItemSelected}
                  key={index}
                >
                  <TableCell padding="checkbox">
                    <Checkbox
                      color="primary"
                      checked={isItemSelected}
                      slotProps={{
                        input: { "aria-labelledby": labelId },
                      }}
                    />
                  </TableCell>
                  {/* biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion */}
                  {visibleHeaderCells.map((cell: any, i: number) => (
                    // owner and responsible person could have the same name
                    <TableCell key={`${cell.id}${i}`}>{animal[cell.id]}</TableCell>
                  ))}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          left: 0,
          bottom: 0,
          width: "calc(100% - 16px)",
          ml: "8px",
          backgroundColor: "#f6f6f6",
        }}
      >
        <Typography sx={{ pl: "16px" }} component="span" variant="body2" color="textPrimary">
          Selected: {selectedAnimalIds.length}
        </Typography>
        <TablePagination
          rowsPerPageOptions={[5, 10, 25, 50].filter((c) => c <= count)}
          component="div"
          count={count}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
        />
      </Box>
    </>
  );
}
