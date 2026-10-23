package com.aegis.decision.exception;

import com.aegis.decision.model.ProblemDetailResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RawPanProhibitedException.class)
    public ResponseEntity<ProblemDetailResponse> handlePanProhibited(
            RawPanProhibitedException ex, HttpServletRequest request) {
        var problem = new ProblemDetailResponse(
                "urn:problem:pan-prohibited",
                "PAN Prohibited",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of(new ProblemDetailResponse.InvalidParameter(ex.getFieldName(), "Raw card numbers (PANs) are strictly prohibited"))
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ProblemDetailResponse.InvalidParameter> params = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new ProblemDetailResponse.InvalidParameter(err.getField(), err.getDefaultMessage()))
                .toList();

        var problem = new ProblemDetailResponse(
                "urn:problem:validation-error",
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                "Request payload failed schema validation constraints.",
                request.getRequestURI(),
                params
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetailResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        var problem = new ProblemDetailResponse(
                "urn:problem:bad-request",
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }
}
