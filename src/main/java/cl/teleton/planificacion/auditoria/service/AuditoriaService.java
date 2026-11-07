package cl.teleton.planificacion.auditoria.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AuditoriaService {

    private final JdbcTemplate jdbc;

    public AuditoriaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(String usuario, String accion, String entidad, String detalle) {
        String d = detalle != null && detalle.length() > 1000 ? detalle.substring(0, 1000) : detalle;
        jdbc.update("INSERT INTO auditoria.registro (usuario, accion, entidad, detalle) VALUES (?,?,?,?)",
                usuario, accion, entidad, d);
    }

    public List<Map<String, Object>> listar(int limite) {
        int n = Math.max(1, Math.min(limite, 500));
        return jdbc.queryForList("SELECT TOP (" + n + ") id, usuario, accion, entidad, detalle, creado_en AS creadoEn " +
                "FROM auditoria.registro ORDER BY id DESC");
    }
}
