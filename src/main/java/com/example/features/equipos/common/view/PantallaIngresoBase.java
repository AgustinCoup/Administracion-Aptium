package com.example.features.equipos.common.view;

import javax.swing.JButton;
import javax.swing.JTextField;
import java.awt.event.ActionListener;

public interface PantallaIngresoBase {
    JTextField getTxtCliente();
    void       setSelectedClienteId(int id);
    int        getSelectedClienteId();
    JButton    getBtnGuardar();
    boolean    isRequiereLavado();
    boolean    isRequiereEmpaque();
    void       setRequiereEmpaqueSelected(boolean v);
    void       setRequiereEmpaqueEnabled(boolean v);
    void       setOnRequiereLavadoChanged(ActionListener l);
    void       limpiarFormulario();
    void       mostrarAdvertencia(String mensaje);
    void       mostrarInfo(String mensaje);
    void       mostrarError(String mensaje);
}
