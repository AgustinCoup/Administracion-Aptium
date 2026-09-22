package com.example.features.lavadero.model;

/**
 * Una máquina del lavadero.
 *
 * <p>{@code activo = false} es una <b>baja lógica</b>, no un borrado: la máquina se retiró del
 * lavadero pero su historia de ciclos sigue colgando de su número. Por eso reactivarla es legítimo
 * —es la misma máquina— y reusar el número para otra no lo es.</p>
 *
 * <p>Ya no lleva {@code capacidadLitros}: la columna se dropeó en la V27 porque ninguna pantalla
 * la mostraba.</p>
 */
public class Lavarropas {

    private final int numero;
    private final boolean activo;

    public Lavarropas(int numero, boolean activo) {
        this.numero = numero;
        this.activo = activo;
    }

    public int getNumero()   { return numero; }
    public boolean isActivo() { return activo; }

    @Override
    public String toString() {
        return "Lavarropas #" + numero;
    }
}
