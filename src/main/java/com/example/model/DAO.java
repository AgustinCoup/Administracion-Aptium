package com.example.model;

import java.util.List;

/**
 * Interfaz genérica para operaciones CRUD (Create, Read, Update, Delete).
 * 
 * Define un contrato estándar que todos los DAOs deben cumplir.
 * Permite crear DAOs consistentes y fáciles de entender.
 * 
 * @param <T> Tipo de entidad que maneja este DAO
 * @param <ID> Tipo del identificador único de la entidad
 */
public interface DAO<T, ID> {
    
    /**
     * Guarda una nueva entidad en la base de datos.
     * Si la entidad ya existe (tiene ID), actualiza la existente.
     * 
     * @param entidad Entidad a guardar
     * @return true si la operación fue exitosa, false si falló
     */
    boolean guardar(T entidad);
    
    /**
     * Obtiene una entidad por su identificador único.
     * 
     * @param id Identificador de la entidad
     * @return La entidad si existe, null si no se encuentra
     */
    T obtenerPorId(ID id);
    
    /**
     * Obtiene todas las entidades del tipo especificado.
     * 
     * @return Lista con todas las entidades, o lista vacía si no hay registros
     */
    List<T> obtenerTodos();
    
    /**
     * Actualiza una entidad existente en la base de datos.
     * 
     * @param entidad Entidad con los datos actualizados
     * @return true si la operación fue exitosa, false si falló
     */
    boolean actualizar(T entidad);
    
    /**
     * Elimina una entidad por su identificador.
     * 
     * @param id Identificador de la entidad a eliminar
     * @return true si la operación fue exitosa, false si falló
     */
    boolean eliminar(ID id);
    
    /**
     * Obtiene el total de registros.
     * 
     * @return Cantidad total de registros en la tabla
     */
    long contar();
    
    /**
     * Verifica si existe una entidad con el ID especificado.
     * 
     * @param id Identificador a verificar
     * @return true si existe, false si no existe
     */
    boolean existe(ID id);
}
