package cl.teleton.planificacion.auth.dto;

public record LoginResponse(String token, String tipo, long expiraEnMinutos,
                            String email, String nombre, String rol, Integer institutoId) {
}
