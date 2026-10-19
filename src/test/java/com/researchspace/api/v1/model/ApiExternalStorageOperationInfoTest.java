package com.researchspace.api.v1.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class ApiExternalStorageOperationInfoTest {

  @Test
  void compareTo_bothRecordIdsNonNull_delegatesToLongCompare() {
    ApiExternalStorageOperationInfo first =
        new ApiExternalStorageOperationInfo(1L, "a", true, null);
    ApiExternalStorageOperationInfo second =
        new ApiExternalStorageOperationInfo(2L, "b", true, null);
    assertThat(first.compareTo(second)).isLessThan(0);
    assertThat(second.compareTo(first)).isGreaterThan(0);
    assertThat(first).isEqualByComparingTo(first);
  }

  @Test
  void compareTo_thisRecordIdNull_sortsAfterOther() {
    ApiExternalStorageOperationInfo withNull =
        new ApiExternalStorageOperationInfo(null, "a", true, null);
    ApiExternalStorageOperationInfo withId =
        new ApiExternalStorageOperationInfo(1L, "b", true, null);
    assertThat(withNull.compareTo(withId)).isGreaterThan(0);
  }

  @Test
  void compareTo_otherRecordIdNull_sortsBeforeOther() {
    ApiExternalStorageOperationInfo withId =
        new ApiExternalStorageOperationInfo(1L, "a", true, null);
    ApiExternalStorageOperationInfo withNull =
        new ApiExternalStorageOperationInfo(null, "b", true, null);
    assertThat(withId.compareTo(withNull)).isLessThan(0);
  }

  @Test
  void compareTo_bothRecordIdsNull_returnsZero() {
    ApiExternalStorageOperationInfo a = new ApiExternalStorageOperationInfo(null, "a", true, null);
    ApiExternalStorageOperationInfo b = new ApiExternalStorageOperationInfo(null, "b", true, null);
    assertThat(a).isEqualByComparingTo(b);
  }

  @Test
  void treeSet_nullRecordIdsSortToEnd() {
    TreeSet<ApiExternalStorageOperationInfo> set = new TreeSet<>();
    ApiExternalStorageOperationInfo nullEntry =
        new ApiExternalStorageOperationInfo(null, "transfer/path.txt", true, null);
    ApiExternalStorageOperationInfo idTwo =
        new ApiExternalStorageOperationInfo(2L, "b", true, null);
    ApiExternalStorageOperationInfo idOne =
        new ApiExternalStorageOperationInfo(1L, "a", true, null);

    set.add(nullEntry);
    set.add(idTwo);
    set.add(idOne);

    assertEquals(1L, set.pollFirst().getRecordId());
    assertEquals(2L, set.pollFirst().getRecordId());
    assertNull(set.pollFirst().getRecordId());
  }
}
