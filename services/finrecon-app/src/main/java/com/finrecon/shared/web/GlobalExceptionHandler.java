package com.finrecon.shared.web;

import java.io.IOException;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// RFC7807 error envelope. Happy paths are untouched; only unhandled
// failures are normalized here.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_ID = "X-Request-Id";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException ex,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        return build(request, response, HttpStatus.BAD_REQUEST,
                ex.getMessage() == null ? "Bad request" : ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException ex,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        return build(request, response, HttpStatus.NOT_FOUND,
                ex.getMessage() == null ? "Not found" : ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleStatus(ResponseStatusException ex,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpStatusCode status = ex.getStatusCode();
        String detail = ex.getReason() == null ? status.toString() : ex.getReason();
        return build(request, response, status, detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleFallback(Exception ex,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        return build(request, response, HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error");
    }

    private static ResponseEntity<ProblemDetail> build(HttpServletRequest request,
            HttpServletResponse response, HttpStatusCode status, String detail) {
        String requestId = request.getHeader(REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            response.setHeader(REQUEST_ID, requestId);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.toString());
        return ResponseEntity.status(status).body(problem);
    }
}
