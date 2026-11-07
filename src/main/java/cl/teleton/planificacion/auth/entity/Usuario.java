package cl.teleton.planificacion.auth.entity;

public record Usuario(int id, String email, String passwordHash, String nombre,
                      String rol, Integer institutoId, boolean activo) {
}
