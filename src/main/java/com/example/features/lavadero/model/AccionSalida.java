package com.example.features.lavadero.model;

/**
 * Qué decide hacer el operador con un conjunto de salidas listas.
 *
 * <p>No confundir con {@link DestinoSalida}, que es lo que se persiste en
 * {@code salidas_lavadero.destino}: dos acciones distintas pueden guardar el mismo destino.
 * Derivar al CDE conservando el cliente y derivar al CDE a nombre de APTIUM son dos acciones
 * con el mismo destino registrado {@code CDE_OTROS}; lo que las diferencia queda en el
 * {@code nro_cliente} del ingreso creado, no en la salida.
 */
public enum AccionSalida {

    FUERA_DE_FLUJO(DestinoSalida.FUERA_DE_FLUJO, "Sale del flujo"),
    CDE_CLIENTE(DestinoSalida.CDE_OTROS, "Ingresa al CDE con su cliente"),
    CDE_APTIUM(DestinoSalida.CDE_OTROS, "Ingresa al CDE como APTIUM");

    private final DestinoSalida destinoPersistido;
    private final String nombre;

    AccionSalida(DestinoSalida destinoPersistido, String nombre) {
        this.destinoPersistido = destinoPersistido;
        this.nombre = nombre;
    }

    /** {@link DestinoSalida} que corresponde persistir para esta acción. */
    public DestinoSalida getDestinoPersistido() {
        return destinoPersistido;
    }

    /** Texto legible para la UI. */
    public String getNombre() {
        return nombre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
