package io.github.neisserdev.problemdetails;

import java.util.Map;

/**
 * El recurso solicitado no existe. Estructurada: conoce qué recurso y qué
 * identificador, para construir un mensaje consistente y exponer ambos como
 * propiedades del ProblemDetail.
 */
public class ResourceNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final String recurso;
    private final transient Object id;

    /**
     * @param recurso nombre legible del recurso (p. ej. "Pedido")
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
