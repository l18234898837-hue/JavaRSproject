package com.htht.common.enums;

import lombok.Getter;

@Getter
public enum DelFlagEnums {

	DELETED("2", "删除"),
	EXISTS("0", "未删除");

    private final String code;
    private final String info;

    DelFlagEnums(String code, String info) {
        this.code = code;
        this.info = info;
    }

}
