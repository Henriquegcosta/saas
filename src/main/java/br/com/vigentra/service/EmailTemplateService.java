package br.com.vigentra.service;

import br.com.vigentra.config.AppProperties;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/** Renderiza os modelos de e-mail (templates/email/*.html) com Thymeleaf. */
@Service
public class EmailTemplateService {

    private final TemplateEngine engine;
    private final AppProperties props;

    public EmailTemplateService(TemplateEngine engine, AppProperties props) {
        this.engine = engine;
        this.props = props;
    }

    public String render(String template, Map<String, Object> variaveis) {
        Context ctx = new Context(Locale.forLanguageTag("pt-BR"));
        ctx.setVariables(variaveis);
        ctx.setVariable("baseUrl", props.baseUrl());
        ctx.setVariable("appName", props.name());
        return engine.process("email/" + template, ctx);
    }
}
