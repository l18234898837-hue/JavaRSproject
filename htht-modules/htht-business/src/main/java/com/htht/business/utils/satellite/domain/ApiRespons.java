package com.htht.business.utils.satellite.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.formula.functions.T;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiRespons<T> {
    private int code;
    private String msg;
    private T data;



}
