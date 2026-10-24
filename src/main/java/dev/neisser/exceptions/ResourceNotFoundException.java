package dev.neisser.exceptions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El recurso solicitado no existe. Estructurada: conoce qué recurso y qué
 * identificador, para construir un mensaje consistente y exponer ambos como
 * propiedades del ProblemDetail.
 *
 * <p>Los servicios pueden lanzar esta excepción de dominio en vez de un 403 (Access Denied)
 * cuando un usuario autenticado intenta acceder a recursos de otro usuario, con el fin de
 * ocultar la existencia del recurso y evitar enumeración de IDs.
 */
public class ResourceNotFoundException extends BusinessException {

    private final String recurso;
    private final transient Object id;

    public ResourceNotFoundException(String recurso, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND,
              "%s con id %s no encontrado".formatted(recurso, id));
        this.recurso = recurso;
        this.id = id;
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
        this.recurso = null;
        this.id = null;
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message, cause);
        this.recurso = null;
        this.id = null;
    }

    public ResourceNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Recurso no encontrado");
        this.recurso = null;
        this.id = null;
    }

    public String getRecurso() {
        return recurso;
    }

    public Object getId() {
        return id;
    }

    @Override
    public Map<String, Object> getProperties() {
        if (recurso == null && id == null) {
            return Map.of();
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        if (recurso != null) {
            properties.put("resource", recurso);
        }
        if (id != null) {
            properties.put("resourceId", String.valueOf(id));
        }
        return Map.copyOf(properties);
    }
}
