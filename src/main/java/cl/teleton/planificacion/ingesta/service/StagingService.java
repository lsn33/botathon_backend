package cl.teleton.planificacion.ingesta.service;

import cl.teleton.planificacion.ingesta.dto.BukRegistro;
import cl.teleton.planificacion.ingesta.dto.BukRequest;
import cl.teleton.planificacion.ingesta.dto.LoteResultado;
import cl.teleton.planificacion.ingesta.dto.SicRegistro;
import cl.teleton.planificacion.ingesta.dto.SicRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Procesa las tablas staging (ingesta.stg_buk / ingesta.stg_sic) donde Blue Prism puede
 * escribir directo en SQL Server, sin pasar por la API.
 */
@Service
public class StagingService {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final IngestaService ingesta;

    public StagingService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, IngestaService ingesta) {
        this.jdbc = jdbc;
        this.named = named;
        this.ingesta = ingesta;
    }

    @Transactional
    public LoteResultado procesarBuk(boolean reporteCompleto, String usuario) {
        List<Long> ids = new ArrayList<>();
        List<BukRegistro> registros = new ArrayList<>();
        String[] archivo = {"STAGING_BUK"};

        jdbc.query("SELECT id, nombre_archivo, persona_id, instituto_id, periodo, cargo_buk, cod_esp, horas_semanales, " +
                "tipo_contrato, tipo_trabajador FROM ingesta.stg_buk WHERE procesado = 0 ORDER BY id", rs -> {
            ids.add(rs.getLong("id"));
            archivo[0] = rs.getString("nombre_archivo");
            registros.add(new BukRegistro(rs.getLong("persona_id"), rs.getInt("instituto_id"),
                    rs.getDate("periodo").toLocalDate(), rs.getString("cargo_buk"), rs.getString("cod_esp"),
                    rs.getBigDecimal("horas_semanales"), rs.getString("tipo_contrato"), rs.getString("tipo_trabajador")));
        });
        if (ids.isEmpty()) {
            return sinPendientes();
        }
        LoteResultado r = ingesta.procesarBuk(
                new BukRequest(archivo[0], null, "BluePrism-Staging", null, reporteCompleto, registros), usuario);
        marcar("ingesta.stg_buk", ids, r.loteId());
        return r;
    }

    @Transactional
    public LoteResultado procesarSic(String usuario) {
        List<Long> ids = new ArrayList<>();
        List<SicRegistro> registros = new ArrayList<>();
        String[] archivo = {"STAGING_SIC"};

        jdbc.query("SELECT id, nombre_archivo, persona_id, instituto_id, periodo, atenciones_realizadas " +
                "FROM ingesta.stg_sic WHERE procesado = 0 ORDER BY id", rs -> {
            ids.add(rs.getLong("id"));
            archivo[0] = rs.getString("nombre_archivo");
            registros.add(new SicRegistro(rs.getLong("persona_id"), rs.getInt("instituto_id"),
                    rs.getDate("periodo").toLocalDate(), rs.getInt("atenciones_realizadas")));
        });
        if (ids.isEmpty()) {
            return sinPendientes();
        }
        LoteResultado r = ingesta.procesarSic(new SicRequest(archivo[0], null, "BluePrism-Staging", null, registros), usuario);
        marcar("ingesta.stg_sic", ids, r.loteId());
        return r;
    }

    /** Respuesta valida para Blue Prism cuando no hay nada que procesar (no es un error). */
    private LoteResultado sinPendientes() {
        return new LoteResultado(0L, "SIN_PENDIENTES", 0, 0, 0, 0, 0, 0);
    }

    /** SQL Server admite maximo 2100 parametros por sentencia: marcamos en bloques de 1000. */
    private void marcar(String tabla, List<Long> ids, long loteId) {
        final int bloque = 1000;
        for (int i = 0; i < ids.size(); i += bloque) {
            List<Long> parte = ids.subList(i, Math.min(i + bloque, ids.size()));
            named.update("UPDATE " + tabla + " SET procesado = 1, lote_id = :lote WHERE id IN (:ids)",
                    new MapSqlParameterSource("lote", loteId).addValue("ids", parte));
        }
    }
}
