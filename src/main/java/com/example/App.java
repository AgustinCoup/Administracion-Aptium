package com.example;

import com.example.controller.AppController;
import com.example.database.ConnectionPool;
import com.example.model.*;
import com.example.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase principal de la aplicación.
 * 
 * ARQUITECTURA CON DEPENDENCY INJECTION:
 * 1. Crea todos los DAOs
 * 2. Inyecta DAOs en Services
 * 3. Inyecta Services en AppModel
 * 4. Inyecta AppModel en Controller
 * 
 * Esta configuración manual es clara y explícita.
 * En proyectos más grandes usarías un framework DI (Spring, Guice).
 */
public class App {
    public static void main(String[] args) {
        Logger log = LoggerFactory.getLogger(App.class);

        // Registrar shutdown hook para cerrar el Connection Pool correctamente
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Cerrando aplicación...");
            ConnectionPool.shutdown();
        }));
        
        // ==================== INICIALIZACIÓN DE BASE DE DATOS ====================
        
        // CRÍTICO: Inicializar esquema ANTES de crear DAOs
        // Esto crea tablas y carga datos iniciales si es necesario
        ConnectionPool.inicializarEsquema();
        
        // ==================== CONFIGURACIÓN DE DEPENDENCIAS ====================
        
        // Capa 1: DAOs (acceso a datos)
        EquipoDAO equipoDAO = new EquipoDAO();
        CatalogoDAO catalogoDAO = new CatalogoDAO();
        ClienteDAO clienteDAO = new ClienteDAO();
        ProfesionalDAO profesionalDAO = new ProfesionalDAO();
        InstitucionDAO institucionDAO = new InstitucionDAO();
        
        // Capa 2: Services (lógica de negocio) - inyectamos DAOs
        EquipoService equipoService = new EquipoService(equipoDAO);
        CatalogoService catalogoService = new CatalogoService(catalogoDAO);
        ClienteService clienteService = new ClienteService(clienteDAO);
        ProfesionalService profesionalService = new ProfesionalService(profesionalDAO);
        InstitucionService institucionService = new InstitucionService(institucionDAO);
        
        // Capa 3: Model (coordinador) - inyectamos Services
        AppModel model = new AppModel(
            equipoService,
            catalogoService,
            clienteService,
            profesionalService,
            institucionService
        );
        
        // Capa 4: Controller (orquestador) - inyectamos Model
        AppController controller = new AppController(model);
        
        // ==================== INICIAR APLICACIÓN ====================
        controller.iniciarAplicacion();
    }
}