package cl.teleton.planificacion.common;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String mensaje) {

    public static ApiError of(int status, String mensaje) {
        return new ApiError(Instant.now(), status, mensaje);
    }
}
