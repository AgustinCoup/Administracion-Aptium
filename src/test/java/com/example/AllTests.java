package com.example;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

// Tests del modelo
import com.example.model.EquipoTest;
import com.example.model.MaterialTest;
import com.example.model.EstadoEquipoTest;

// Tests de servicios
import com.example.service.EquipoServiceTest;
import com.example.service.LoteServiceTest;
import com.example.service.InstitucionServiceTest;

// Tests de vistas/helpers
import com.example.view.helpers.AutoclaveTableModelTest;
import com.example.view.helpers.MaterialTableModelTest;

// Tests de utilidades
import com.example.util.ValidadorTest;

// Tests de integración
import com.example.integration.GestionEquiposIntegrationTest;
import com.example.integration.DAOsIntegrationTest;
import com.example.integration.ServiciosIntegrationTest;

/**
 * Suite de tests para la aplicación Administración Aptium.
 * 
 * Organización:
 * 1. Tests de Modelo: Validan lógica de negocio en entidades (Equipo, Material, EstadoEquipo)
 * 2. Tests de Servicios: Validan lógica de negocio con mocks (EquipoService, LoteService, InstitucionService)
 * 3. Tests de Vistas: Validan TableModels (AutoclaveTableModel, MaterialTableModel)
 * 4. Tests de Utilidades: Validan funciones de validación (Validador)
 * 5. Tests de Integración: Validan flujos completos end-to-end con base de datos real
 *    - GestionEquiposIntegrationTest: Flujos completos de equipos
 *    - DAOsIntegrationTest: Todos los DAOs con código real (sin mocks)
 *    - ServiciosIntegrationTest: Todos los servicios ejecutando código real
 * 
 * Para ejecutar:
 * - Todos los tests: mvn test
 * - Solo esta suite: mvn test -Dtest=AllTests
 * - Un test específico: mvn test -Dtest=EquipoServiceTest
 */
@RunWith(Suite.class)
@SuiteClasses({
    // ===== TESTS DE MODELO =====
    EquipoTest.class,
    MaterialTest.class,
    EstadoEquipoTest.class,
    
    // ===== TESTS DE SERVICIOS =====
    EquipoServiceTest.class,
    LoteServiceTest.class,
    InstitucionServiceTest.class,
    
    // ===== TESTS DE VISTAS =====
    AutoclaveTableModelTest.class,
    MaterialTableModelTest.class,
    
    // ===== TESTS DE UTILIDADES =====
    ValidadorTest.class,
    
    // ===== TESTS DE INTEGRACIÓN =====
    GestionEquiposIntegrationTest.class,
    DAOsIntegrationTest.class,
    ServiciosIntegrationTest.class
})
public class AllTests {
    // Esta clase solo sirve como contenedor para la suite
}
