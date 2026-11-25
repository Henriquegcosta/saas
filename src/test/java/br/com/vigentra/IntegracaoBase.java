package br.com.vigentra;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Base dos testes de integração: contexto Spring completo + MockMvc + banco H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracaoBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestSupport ts;

    @AfterEach
    void limparContexto() {
        ts.deslogar();
    }
}
