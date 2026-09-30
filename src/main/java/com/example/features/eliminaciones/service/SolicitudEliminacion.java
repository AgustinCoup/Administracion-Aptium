package com.example.features.eliminaciones.service;

import com.example.common.eliminacion.ResumenEliminacion;

import java.util.Objects;

/**
 * Lo que el operador confirmó: el resumen que vio (que trae el token de guarda) y el motivo.
 *
 * <p><b>La password no va acá.</b> Un {@code record} genera un {@code toString} que imprime sus
 * componentes, y un componente {@code char[]} imprimiría la identidad del array; además la
 * tentaría a viajar más lejos de lo necesario. Viaja aparte, como argumento de
 * {@link EliminacionIngresosService#eliminar}, y la limpia quien la creó.</p>
 */
public record SolicitudEliminacion(ResumenEliminacion resumen, String motivo) {

    public SolicitudEliminacion {
        Objects.requireNonNull(resumen, "resumen");
    }
}
