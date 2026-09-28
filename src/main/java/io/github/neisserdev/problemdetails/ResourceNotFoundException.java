package io.github.neisserdev.problemdetails;

import java.util.Map;

/**
 * Recurso inexistente. Responde 404 e incluye {@code resource} y {@code resourceId} en el JSON.
 */
public class ResourceNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final String recurso;
    private final transient Object id;

    /**
     * @param recurso nombre del recurso, por ejemplo "Pedido"
     * @param id      identificador buscado
     */
    public ResourceNotFoundException(String recurso, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND,
              "%s con id %s no encontrado".formatted(recurso, id));
        this.recurso = recurso;
        this.id = id;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("resource", recurso, "resourceId", String.valueOf(id));
    }
}
