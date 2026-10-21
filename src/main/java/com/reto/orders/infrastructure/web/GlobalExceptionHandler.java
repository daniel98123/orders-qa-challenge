package com.reto.orders.infrastructure.web;

import com.reto.orders.domain.OrderStatus;
import com.reto.orders.domain.exception.DeliveredOrderCancellationException;
import com.reto.orders.domain.exception.DomainException;
import com.reto.orders.domain.exception.InvalidOrderException;
import com.reto.orders.domain.exception.InvalidStateTransitionException;
import com.reto.orders.domain.exception.OrderNotFoundException;
import com.reto.orders.domain.exception.OrderNotPaidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traduce errores a respuestas RFC 7807 (application/problem+json) con:
 * <ul>
 *   <li>{@code code}: regla de negocio violada (RN-0x) o tipo de error, para trazabilidad.</li>
 *   <li>{@code correlationId}: para cruzar la respuesta con los logs.</li>
 * </ul>
 * Mapeo de códigos HTTP: 400 datos inválidos y RN-05 (exigido por el reto); 404 recurso inexistente;
 * 409 conflicto con el estado actual (RN-04, RN-06); 500 error inesperado sin filtrar detalles internos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(OrderNotFoundException ex) {
        return domainProblem(HttpStatus.NOT_FOUND, "Orden no encontrada", ex);
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrder(InvalidOrderException ex) {
        return domainProblem(HttpStatus.BAD_REQUEST, "Orden inválida", ex);
    }

    @ExceptionHandler(OrderNotPaidException.class)
    public ResponseEntity<ProblemDetail> handleNotPaid(OrderNotPaidException ex) {
        return domainProblem(HttpStatus.BAD_REQUEST, "Orden no pagada", ex);
    }

    @ExceptionHandler(DeliveredOrderCancellationException.class)
    public ResponseEntity<ProblemDetail> handleDeliveredCancellation(DeliveredOrderCancellationException ex) {
        return domainProblem(HttpStatus.CONFLICT, "Orden entregada", ex);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransition(InvalidStateTransitionException ex) {
        return domainProblem(HttpStatus.CONFLICT, "Transición de estado inválida", ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("event=unexpected_error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Reporte el correlationId al equipo de soporte.");
        problem.setTitle("Error interno");
        enrich(problem, "INTERNAL_ERROR");
        return ResponseEntity.internalServerError().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "campo", error.getField(),
                        "mensaje", String.valueOf(error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La petición tiene errores de validación");
        problem.setTitle("Petición inválida");
        problem.setProperty("errors", errors);
        enrich(problem, "VALIDATION");
        log.warn("event=validation_failed errors={}", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es un JSON válido o contiene valores no permitidos. "
                        + "Estados válidos: " + Arrays.toString(OrderStatus.values()));
        problem.setTitle("Petición mal formada");
        enrich(problem, "MALFORMED_REQUEST");
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Parámetro de ruta o query con un tipo inválido (id que no es UUID, estado inexistente en el filtro).
     * El valor recibido no se escribe en el log para evitar inyección de logs (CWE-117).
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String parametro = ex.getPropertyName();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El valor '" + ex.getValue() + "' no es válido para '" + parametro + "': "
                        + valorEsperado(ex.getRequiredType()));
        problem.setTitle("Parámetro inválido");
        enrich(problem, "INVALID_PARAMETER");
        log.warn("event=invalid_parameter parameter={}", parametro);
        return ResponseEntity.badRequest().body(problem);
    }

    static String valorEsperado(Class<?> tipo) {
        if (UUID.class.equals(tipo)) {
            return "debe ser un UUID completo, p. ej. 3f2b8c1e-7a4d-4e8f-9b21-6c0d5e4f3a12";
        }
        if (tipo != null && tipo.isEnum()) {
            return "valores permitidos " + Arrays.toString(tipo.getEnumConstants());
        }
        return "tipo de dato incorrecto";
    }

    /** Agrega code/correlationId a los errores estándar de Spring MVC (405, 415, etc.). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            enrich(problem, "HTTP_" + statusCode.value());
        }
        return response;
    }

    private ResponseEntity<ProblemDetail> domainProblem(HttpStatus status, String title, DomainException ex) {
        log.warn("event=business_rule_violation code={} status={} detail=\"{}\"",
                ex.getCode(), status.value(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(title);
        enrich(problem, ex.getCode());
        return ResponseEntity.status(status).body(problem);
    }

    private void enrich(ProblemDetail problem, String code) {
        problem.setProperty("code", code);
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        problem.setProperty("timestamp", Instant.now().toString());
    }
}
