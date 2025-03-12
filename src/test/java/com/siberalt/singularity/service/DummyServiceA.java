package com.siberalt.singularity.service;

import java.util.List;

public class DummyServiceA implements DummyInterface {
    protected String stringField;
    protected int intField;
    protected boolean booleanField;
    protected DummyServiceB dummyServiceB;
    protected List<?> listField;

    public String getStringField() {
        return stringField;
    }

    public void setStringField(String stringField) {
        this.stringField = stringField;
    }

    public int getIntField() {
        return intField;
    }

    public void setIntField(int intField) {
        this.intField = intField;
    }

    public boolean isBooleanField() {
        return booleanField;
    }

    public void setBooleanField(boolean booleanField) {
        this.booleanField = booleanField;
    }

    public DummyServiceB getDummyServiceB() {
        return dummyServiceB;
    }

    public void setDummyServiceB(DummyServiceB dummyServiceB) {
        this.dummyServiceB = dummyServiceB;
    }

    public List<?> getListField() {
        return listField;
    }

    public void setListField(List<?> listField) {
        this.listField = listField;
    }
}
