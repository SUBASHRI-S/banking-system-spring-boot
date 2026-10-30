package com.banksim.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Builds the single error shape used everywhere (controllers and security filters). */
public final class ErrorResponses {

    private ErrorResponses() {
    }

    public static Map<String, Object> body(HttpStatus status, String code, String title, String detail, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);
        body.put("timestamp", Instant.now().toString());
        body.put("path", path);
        return body;
    }

    /** Used by the security filters, which run before Spring MVC and cannot use the controller advice. */
    public static void write(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String code,
                             String title, String detail, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), body(status, code, title, detail, path));
    }
}
