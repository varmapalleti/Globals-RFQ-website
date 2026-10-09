package com.globalrfq.identity.common.exception;

public class ResourceNotFoundException extends BusinessException{

    public ResourceNotFoundException(String resource, Object id){
        super(ErrorCode.RESOURCE_NOT_FOUND, resource + "not found with id: " + id);
    }

    public ResourceNotFoundException(String message){
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
