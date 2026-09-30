package com.example.common.eliminacion;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Cómo se serializa un ingreso para {@code ingresos_eliminados.snapshot}.
 *
 * <p>Existe para que cada eliminador escriba <em>qué tablas</em> archiva y no <em>cómo</em>
 * serializarlas: un solo lugar decide los nombres de las claves, el formato de las fechas y qué es
 * un {@code NULL}. Clase plana, sin estado.</p>
 */
public final class SnapshotJson {

    private SnapshotJson() {}

    /**
     * La raíz del árbol. {@code formato} permite cambiar el contenido más adelante sin migrar las
     * filas viejas: quien lea el archivo sabe con qué forma se escribió cada una.
     */
    public static JSONObject raiz(ModuloIngreso modulo, int formato) {
        return new JSONObject()
            .put("modulo", modulo.name())
            .put("formato", formato);
    }

    /** Consume el {@link ResultSet} entero: una fila, un objeto. */
    public static JSONArray filas(ResultSet rs) throws SQLException {
        JSONArray filas = new JSONArray();
        while (rs.next()) {
            filas.put(filaActual(rs));
        }
        return filas;
    }

    /**
     * La fila en la que está parado el {@link ResultSet}, sin moverlo.
     *
     * <ul>
     *   <li>Las claves son los nombres de columna <b>en minúsculas</b>: H2 los devuelve en
     *       mayúsculas y MySQL como están en el esquema, y un mismo ingreso se tiene que archivar
     *       igual en los dos.</li>
     *   <li>Fechas en ISO-8601 ({@code 2026-09-29T10:15:00}), sin zona: son las mismas horas
     *       locales que muestran las pantallas.</li>
     *   <li>{@code NULL} es {@link JSONObject#NULL}, no una clave ausente: "no tenía valor" y "no
     *       se archivó esa columna" no son lo mismo.</li>
     * </ul>
     */
    public static JSONObject filaActual(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        JSONObject fila = new JSONObject();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            fila.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), valor(rs, i));
        }
        return fila;
    }

    private static Object valor(ResultSet rs, int columna) throws SQLException {
        Object valor = rs.getObject(columna);
        if (valor == null) {
            return JSONObject.NULL;
        }
        if (valor instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        if (valor instanceof LocalDateTime ldt) {
            return ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        if (valor instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (valor instanceof LocalDate ld) {
            return ld.toString();
        }
        if (valor instanceof Clob) {  // TEXT en H2 (remito_observaciones)
            return rs.getString(columna);
        }
        if (valor instanceof Number || valor instanceof Boolean || valor instanceof String) {
            return valor;
        }
        return valor.toString();
    }
}
