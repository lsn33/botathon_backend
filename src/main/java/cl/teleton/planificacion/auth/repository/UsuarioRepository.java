package cl.teleton.planificacion.auth.repository;

import cl.teleton.planificacion.auth.entity.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UsuarioRepository {

    private static final RowMapper<Usuario> MAPPER = (rs, i) -> new Usuario(
            rs.getInt("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("nombre"),
            rs.getString("rol"),
            rs.getObject("instituto_id", Integer.class),
            rs.getBoolean("activo"));

    private final JdbcTemplate jdbc;

    public UsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Usuario> porEmail(String email) {
        return jdbc.query("SELECT id, email, password_hash, nombre, rol, instituto_id, activo " +
                "FROM seguridad.usuario WHERE email = ?", MAPPER, email).stream().findFirst();
    }

    public boolean existe(String email) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM seguridad.usuario WHERE email = ?", Integer.class, email);
        return n != null && n > 0;
    }

    public void crear(String email, String passwordHash, String nombre, String rol, Integer institutoId) {
        jdbc.update("INSERT INTO seguridad.usuario (email, password_hash, nombre, rol, instituto_id) VALUES (?,?,?,?,?)",
                email, passwordHash, nombre, rol, institutoId);
    }
}
