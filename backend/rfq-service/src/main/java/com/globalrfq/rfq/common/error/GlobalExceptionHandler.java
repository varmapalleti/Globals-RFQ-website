package com.globalrfq.rfq.common.error;

import com.globalrfq.rfq.common.api.ApiErrorResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RfqException.class)
    public ResponseEntity<ApiErrorResponse> handleRfq(RfqException exception, HttpServletRequest request) {
        return response(exception.code(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError).toList();
        return response(RfqErrorCode.VALIDATION_FAILED, "Request validation failed", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(RfqErrorCode.BAD_REQUEST, "Request body is missing or malformed", request, null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(Exception exception, HttpServletRequest request) {
        return response(RfqErrorCode.VALIDATION_FAILED, "A query parameter, path value, or request field has an invalid value", request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadSize(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response(RfqErrorCode.PAYLOAD_TOO_LARGE, "The uploaded file exceeds the configured request size limit", request, null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipart(MultipartException exception, HttpServletRequest request) {
        return response(RfqErrorCode.BAD_REQUEST, "The multipart upload is malformed or exceeds its configured limits", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethod(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return response(RfqErrorCode.METHOD_NOT_ALLOWED, "This HTTP method is not supported for the requested endpoint", request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return response(RfqErrorCode.UNSUPPORTED_MEDIA_TYPE, "This endpoint does not support the supplied content type", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(RfqErrorCode.DUPLICATE_RESOURCE, "A conflicting or invalid record already exists", request, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrent(ObjectOptimisticLockingFailureException exception, HttpServletRequest request) {
        return response(RfqErrorCode.CONCURRENT_MODIFICATION, "This RFQ changed while you were editing it. Reload and retry", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled error for {} {} correlationId={}", request.getMethod(), request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY), exception);
        return response(RfqErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Contact support with the correlation ID", request, null);
    }

    private ResponseEntity<ApiErrorResponse> response(RfqErrorCode code, String message, HttpServletRequest request,
                                                       List<ApiErrorResponse.FieldError> fields) {
        int status = code.status().value();
        return ResponseEntity.status(status).body(new ApiErrorResponse(false, status, code.name(), message,
                request.getRequestURI(), fields, Instant.now(), MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    private static ApiErrorResponse.FieldError fieldError(FieldError error) {
        return new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage());
    }
}
