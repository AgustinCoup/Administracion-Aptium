package com.example.common.eliminacion;

import java.net.InetAddress;

/**
 * Desde dónde se hizo algo: {@code usuario@hostname}.
 *
 * <p>La app no tiene usuarios, así que esto es lo único que dice qué puesto borró un ingreso. Es
 * informativo: <b>nunca lanza</b>, porque no poder resolver el hostname no puede impedir un
 * borrado. Si el hostname falla, queda sólo el usuario.</p>
 */
public final class PuestoDeTrabajo {

    /** El {@code VARCHAR(255)} de {@code ingresos_eliminados.puesto}. */
    static final int LARGO_MAXIMO = 255;

    private static final String DESCONOCIDO = "desconocido";

    private PuestoDeTrabajo() {}

    public static String actual() {
        String usuario = usuario();
        String host = host();
        String puesto = host == null ? usuario : usuario + "@" + host;
        return puesto.length() <= LARGO_MAXIMO ? puesto : puesto.substring(0, LARGO_MAXIMO);
    }

    private static String usuario() {
        try {
            String usuario = System.getProperty("user.name");
            return usuario == null || usuario.isBlank() ? DESCONOCIDO : usuario;
        } catch (RuntimeException e) {  // SecurityException de un SecurityManager
            return DESCONOCIDO;
        }
    }

    private static String host() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {  // UnknownHostException o SecurityException: el puesto es informativo
            return null;
        }
    }
}
