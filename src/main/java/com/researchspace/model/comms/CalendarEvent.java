package com.researchspace.model.comms;

import lombok.Data;

import java.util.Date;

@Data
public class CalendarEvent {
    private String title;

    // String fields from the front-end
    private String start;
    private String end;
    // Parsed dates
    private Date startTime;
    private Date endTime;

    private String description;

    private String frequency;
    private Long occurrences;

    private String attachments;
}
