package cl.teleton.planificacion.ingesta.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class LoteRepository {

    private final JdbcTemplate jdbc;

    public LoteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existeHashProcesado(String hash, String tipo) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM ingesta.lote WHERE hash_sha256 = ? AND tipo = ? " +
                "AND estado IN ('OK','CON_ERRORES')", Integer.class, hash, tipo);
        return n != null && n > 0;
    }

    public long crear(String tipo, String archivo, String hash, String bpProceso, String bpSesion, boolean completo) {
        Map<String, Object> f = new HashMap<>();
        f.put("tipo", tipo);
        f.put("nombre_archivo", archivo);
        f.put("hash_sha256", hash == null || hash.isBlank() ? null : hash);
        f.put("bp_proceso", bpProceso);
        f.put("bp_sesion_id", bpSesion);
        f.put("reporte_completo", completo);
        return new SimpleJdbcInsert(jdbc).withSchemaName("ingesta").withTableName("lote")
                .usingGeneratedKeyColumns("id").executeAndReturnKey(f).longValue();
    }

    public void cerrar(long id, String estado, int total, int nuevos, int modificados, int sinCambios, int bajas, int errores) {
        jdbc.update("UPDATE ingesta.lote SET estado = ?, total = ?, nuevos = ?, modificados = ?, sin_cambios = ?, " +
                "bajas = ?, errores = ? WHERE id = ?", estado, total, nuevos, modificados, sinCambios, bajas, errores, id);
    }

    public void agregarError(long loteId, int fila, String mensaje) {
        String m = mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje;
        jdbc.update("INSERT INTO ingesta.error (lote_id, fila, mensaje) VALUES (?,?,?)", loteId, fila, m);
    }

    public List<Map<String, Object>> listar(int limite) {
        int n = Math.max(1, Math.min(limite, 500));
        return jdbc.queryForList("SELECT TOP (" + n + ") id, tipo, nombre_archivo AS nombreArchivo, " +
                "bp_proceso AS bpProceso, bp_sesion_id AS bpSesionId, reporte_completo AS reporteCompleto, estado, " +
                "total, nuevos, modificados, sin_cambios AS sinCambios, bajas, errores, creado_en AS creadoEn " +
                "FROM ingesta.lote ORDER BY id DESC");
    }

    public List<Map<String, Object>> errores(long loteId) {
        return jdbc.queryForList("SELECT id, fila, mensaje FROM ingesta.error WHERE lote_id = ? ORDER BY fila", loteId);
    }
}
