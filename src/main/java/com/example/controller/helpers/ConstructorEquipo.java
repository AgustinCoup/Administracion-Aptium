package com.example.controller.helpers;

import com.example.model.AppModel;
import com.example.model.Equipo;
import com.example.model.Material;
import com.example.util.Validador;
import com.example.view.PantallaIngresoOrtopedia;

/**
 * Encapsula la lógica de construcción de un objeto Equipo a partir de los datos del formulario.
 * 
 * Responsabilidad: Mapear datos de la Vista hacia el Modelo.
 * 
 * Separación de responsabilidades:
 * - OrthopediaInputController solo coordina, no mapea
 * - Esta clase contiene toda la lógica de conversión (Vista → Modelo)
 */
public class ConstructorEquipo {
    
    private final PantallaIngresoOrtopedia panel;
    private final AppModel model;
    
    public ConstructorEquipo(PantallaIngresoOrtopedia panel, AppModel model) {
        this.panel = panel;
        this.model = model;
    }
    
    /**
     * Construye un objeto Equipo a partir de los datos del formulario.
     * 
     * Campos obligatorios: cliente, institución
     * Campos opcionales: profesional, paciente (pueden ser null o vacío)
     * 
     * @return Equipo con todos los datos del formulario
     */
    public Equipo construir() {
        Equipo equipo = new Equipo();
        
        equipo.setClienteNombre(panel.getTxtCliente().getText().trim());
        
        /**
         * El identificador del cliente ahora proviene de la selección
         * del autocompletado, no de un valor hardcodeado.
         * 
         * Garantiza que nroCliente sea válido según la FK
         * hacia la tabla clientes.
         */
        equipo.setNroCliente(panel.getSelectedClienteId());
        
        /**
         * El profesional es OPCIONAL.
         * Solo se establece si fue seleccionado desde el autocompletado.
         */
        if (panel.getSelectedProfesionalId() != -1) {
            equipo.setNroProfesional(panel.getSelectedProfesionalId());
            equipo.setProfesionalNombre(panel.getTxtProfesional().getText().trim());
        }
        
        /**
         * El paciente es OPCIONAL.
         * Se establece si el campo no está vacío.
         */
        String paciente = panel.getTxtPaciente().getText().trim();
        if (!paciente.isEmpty()) {
            equipo.setPacienteNombre(paciente);
        }
        
        /**
         * La institución es OBLIGATORIA y debe ser seleccionada del autocompletado.
         * El identificador se utiliza como FK en la tabla instituciones.
         */
        equipo.setNroInstitucion(panel.getSelectedInstitucionId());
        
        /**
         * Agregar materiales: itera sobre las filas del panel de materiales
         * y construye objetos Material con código, descripción y cantidad.
         */
        panel.getMaterialRows().forEach(row -> {
            String numeroStr = row.numero.getText().trim();
            
            if (!numeroStr.isEmpty() && Validador.soloNumeros(numeroStr)) {
                int codigoMaterial = Integer.parseInt(numeroStr);
                String descripcion = row.descripcion.getText().trim();
                Object ElementosObj = row.Elementos.getValue();
                
                int cantidad = 0;
                if (ElementosObj instanceof Number) {
                    cantidad = ((Number) ElementosObj).intValue();
                }
                
                Material material = new Material(codigoMaterial, descripcion, cantidad);
                equipo.agregarMaterial(material);
            }
        });
        
        return equipo;
    }
}
