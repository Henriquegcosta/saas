package br.com.vigentra.unit;

import br.com.vigentra.util.RequestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestUtilsTest {

    @AfterEach
    void limparContextoDeRequisicao() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void retornaVazioQuandoNaoExisteRequisicaoAtual() {
        assertThat(RequestUtils.requestAtual()).isEmpty();
        assertThat(RequestUtils.ipCliente()).isNull();
    }

    @Test
    void retornaRequisicaoAtualEOEnderecoRemoto() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(RequestUtils.requestAtual()).contains(request);
        assertThat(RequestUtils.ipCliente()).isEqualTo("192.0.2.10");
    }
}
