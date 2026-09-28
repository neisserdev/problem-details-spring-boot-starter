package io.github.neisserdev.problemdetails;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void cadaStatusCanonicoTieneUnRepresentante() {
        // toUnmodifiableMap falla si dos códigos comparten status
        assertThat(ErrorCode.porStatus(400)).contains(ErrorCode.MALFORMED_REQUEST);
        assertThat(ErrorCode.porStatus(404)).contains(ErrorCode.ENDPOINT_NOT_FOUND);
        assertThat(ErrorCode.porStatus(413)).contains(ErrorCode.CONTENT_TOO_LARGE);
        assertThat(ErrorCode.porStatus(422)).contains(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void losStatusSinRepresentanteNoCaenEnErrorInterno() {
        assertThat(ErrorCode.porStatus(410)).isEmpty();
        assertThat(ErrorCode.porStatus(402)).isEmpty();
    }

    @Test
    void todosLosCodigosSonValidosDentroDeUnUri() {
        ProblemDetailsFactory fabrica = new ProblemDetailsFactory();
        for (ErrorCode codigo : ErrorCode.values()) {
            assertThat(fabrica.tipoDe(codigo)).hasToString("/problems/" + codigo.name());
        }
    }
}
