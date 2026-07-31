package com.htht.common.utils.excel;

public class ImportValidationException extends RuntimeException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private final Object invalidValue;

    public ImportValidationException(String message, Object invalidValue) {
        super(message);
        this.invalidValue = invalidValue;
    }

    public Object getInvalidValue() {
        return invalidValue;
    }
}