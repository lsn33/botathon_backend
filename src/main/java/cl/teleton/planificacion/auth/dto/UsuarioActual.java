package cl.teleton.planificacion.auth.dto;

/** Usuario autenticado (principal en el SecurityContext). */
public record UsuarioActual(String email, String rol, Integer institutoId) {
}
