package com.globalrfq.identity.common.exception;


import com.globalrfq.identity.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import javax.naming.AuthenticationException;
import java.nio.file.AccessDeniedException;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

//    Helper method...

    private ResponseEntity<ApiErrorResponse> build(ErrorCode code, String message, HttpServletRequest req, List<ApiErrorResponse.FieldErrorDetail> fieldErrors){
        return ResponseEntity.status(code.getStatus()).body(ApiErrorResponse.of(code, message, req.getRequestURI(), fieldErrors));
    }

//    GlobalException handler methods....

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest req){
        log.warn("Business error [{}]", ex.getErrorCode(), ex.getMessage());
        return build(ex.getErrorCode(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req){
        List<ApiErrorResponse.FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream().map(fe -> new ApiErrorResponse.FieldErrorDetail(fe.getField(), fe.getDefaultMessage())).toList();
        log.warn("Validation failed on {}: {} field error(s)", req.getRequestURI(), errors.size());
        return build(ErrorCode.VALIDATION_FAILED, "Validation failed", req, errors);
    }

    @ExceptionHandler(ConstraintDeclarationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest req){
        List<ApiErrorResponse.FieldErrorDetail> errors = ex.getConstraintViolations().stream().map(v-> new ApiErrorResponse.FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage())).toList();
        return build(ErrorCode.VALIDATION_FAILED, "Validation failed", req, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req){
        return build(ErrorCode.MALFORMED_REQUEST, "Request body is missing or malformed", req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req){

        return build(ErrorCode.BAD_REQUEST, "Invalid value for parameter: "+ ex.getName(), req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req){
        return build(ErrorCode.METHOD_NOT_ALLOWED, "Http method not supported for this endpoint", req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest req){
        return build(ErrorCode.RESOURCE_NOT_FOUND, "Endpoint not found", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req){
        log.warn("Access Denied on {}",  req.getRequestURI());
        return build(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest req){
        return build(ErrorCode.UNAUTHORIZED, "Authentication is required", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {}", req.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "Something went wrong. Please contact support with the correlation ID.", req, null);

    }
}
