## Testing the CSV import with IGSN Identifiers (RSDEV-631)

This is a guide to test the feature to import IGSN from the CSV file into Inventory items

### Prerequisite

- Inventory is `enabled`
- Datacite is `enabled` and correctly `configured`
- Bulk register 14 new unassociated IGSNs from Rspace
  Inventory
- - `https://yourinstance.researchspace.com/inventory/identifiers/igsn`
- Substitute each identifier created on RSpace into the following 3 CSV files where you see the
  pattern `[check src/test/resources/TestResources/inventory/igsn/README.md]`
- - `src/test/resources/TestResources/inventory/igsn/container_import_all_columns_igsn.csv`
- - `src/test/resources/TestResources/inventory/igsn/sample_import_into_containers_igsn.csv`
- - `src/test/resources/TestResources/inventory/igsn/subsample_import_into_containers_igsn.csv`

### Test

- You can now import the CSV files into the inventory from the proper import menu