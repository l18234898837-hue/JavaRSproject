package com.htht.multisource.application.usecase;
public final class ArtifactAccessDeniedException extends IllegalStateException { private final String code; public ArtifactAccessDeniedException(String code,String message){super(message);this.code=code;} public String getCode(){return code;} }
