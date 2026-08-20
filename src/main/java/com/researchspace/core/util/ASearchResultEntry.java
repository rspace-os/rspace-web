package com.researchspace.core.util;

public abstract class ASearchResultEntry<T> {

    private T entry;

    public ASearchResultEntry(T entry){
        this.entry = entry;
    }

    public T entry() {
        return entry;
    }
    public void setEntry(T e){ entry = e; }
}