package com.htht.common.enums;

public enum YesNoEnums {

	YES(1, "是"), 
	NO(0, "否");

    private final int code;
    private final String info;

    YesNoEnums(int code, String info) {
        this.code = code;
        this.info = info;
    }

    public int getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }
    
}
