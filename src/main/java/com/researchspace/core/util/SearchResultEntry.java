package com.researchspace.core.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchResultEntry<T> extends ASearchResultEntry<T> {

    private Map<String, String> highlights = new HashMap<>();

    // TODO: add information about the scoring in the future
    private int score = 0;

    public SearchResultEntry(T entry) {
        super(entry);
    }

    public SearchResultEntry(T entry, Map<String, String> highlights) {
        this(entry);
        this.highlights = highlights;
    }
    public SearchResultEntry(T entry, int score) {
        this(entry);
        this.score = score;
    }

    public Map<String, String> getHighlights() {
        return highlights;
    }

    public int getScore() {
        return score;
    }

    public String getHighlightedField(String fieldId){
        if(highlights.containsKey(fieldId)){
            return highlights.get(fieldId);
        }

        throw new IllegalArgumentException("The field " + fieldId +
                " is not present in the highlighted data of class " + entry().getClass().toString());
    }

    public static <T> List<SearchResultEntry<T>> wrapList(List<T> list){
        return list.stream().map(SearchResultEntry::new).collect(Collectors.toList());
    }
}