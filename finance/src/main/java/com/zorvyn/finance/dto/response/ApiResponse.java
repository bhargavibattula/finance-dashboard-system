package com.zorvyn.finance.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Universal response wrapper.
 *
 * Every endpoint returns this shape — success or error — so the frontend
 * never has to guess what structure it is getting back.
 *
 * @JsonInclude(NON_NULL) drops null fields from JSON output:
 *   a success response has no "errors" key,
 *   an error response with no data has no "data" key.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean           success;
    private String            message;
    private T                 data;
    private List<String>      errors;
    private LocalDateTime     timestamp;

    private ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        r.data    = data;
        return r;
    }

    public static <T> ApiResponse<T> ok(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String message, List<String> errors) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        r.errors  = errors;
        return r;
    }

    public boolean       isSuccess()   { return success; }
    public String        getMessage()  { return message; }
    public T             getData()     { return data; }
    public List<String>  getErrors()   { return errors; }
    public LocalDateTime getTimestamp(){ return timestamp; }
}