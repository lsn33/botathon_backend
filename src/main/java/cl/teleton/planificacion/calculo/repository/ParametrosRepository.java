package cl.teleton.planificacion.calculo.repository;

import cl.teleton.planificacion.calculo.dto.DistribucionDto;
import cl.teleton.planificacion.calculo.dto.ParametroDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ParametrosRepository {

    private final JdbcTemplate jdbc;

    public ParametrosRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ParametroDto> general() {
        return jdbc.query("SELECT clave, valor, descripcion FROM parametros.parametro ORDER BY clave",
                (rs, i) -> new ParametroDto(rs.getString("clave"), rs.getBigDecimal("valor"), rs.getString("descripcion")));
    }

    public Map<String, BigDecimal> generalComoMapa() {
        Map<String, BigDecimal> m = new HashMap<>();
        general().forEach(p -> m.put(p.clave(), p.valor()));
        return m;
    }

    public boolean actualizarGeneral(String clave, BigDecimal valor) {
        return jdbc.update("UPDATE parametros.parametro SET valor = ? WHERE clave = ?", valor, clave) > 0;
    }

    public List<DistribucionDto> distribuciones() {
        return jdbc.query("SELECT cod_esp, tramo, pct_directo, pct_indirecto, pct_nas, minutos_directa " +
                        "FROM parametros.distribucion ORDER BY cod_esp, tramo",
                (rs, i) -> new DistribucionDto(rs.getString("cod_esp"), rs.getString("tramo"),
                        rs.getBigDecimal("pct_directo"), rs.getBigDecimal("pct_indirecto"),
                        rs.getBigDecimal("pct_nas"), rs.getInt("minutos_directa")));
    }

    public void upsertDistribucion(DistribucionDto d) {
        int n = jdbc.update("UPDATE parametros.distribucion SET pct_directo = ?, pct_indirecto = ?, pct_nas = ?, " +
                        "minutos_directa = ? WHERE cod_esp = ? AND tramo = ?",
                d.pctDirecto(), d.pctIndirecto(), d.pctNas(), d.minutosDirecta(), d.codEsp(), d.tramo());
        if (n == 0) {
            jdbc.update("INSERT INTO parametros.distribucion (cod_esp, tramo, pct_directo, pct_indirecto, pct_nas, " +
                            "minutos_directa) VALUES (?,?,?,?,?,?)",
                    d.codEsp(), d.tramo(), d.pctDirecto(), d.pctIndirecto(), d.pctNas(), d.minutosDirecta());
        }
    }
}
