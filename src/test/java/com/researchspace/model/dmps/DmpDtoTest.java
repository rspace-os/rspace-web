package com.researchspace.model.dmps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DmpDtoTest {

    @Test
    @DisplayName("equality based on dmpId and source")
    void testEquals() {
        DmpDto dmpDto1 = new DmpDto("dmpID", "Title");
        DmpDto dmpDto2 = new DmpDto("dmpID", "Title");

        assertEquals(dmpDto1, dmpDto2);
        assertEquals(dmpDto1.hashCode(), dmpDto2.hashCode());
    }
}