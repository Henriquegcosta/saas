package br.com.vigentra;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Categoria;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.CategoriaRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioDetailsService;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.EmpresaService;
import br.com.vigentra.service.UsuarioService;
import br.com.vigentra.web.dto.DocumentoForm;
import br.com.vigentra.web.dto.RegistroForm;
import br.com.vigentra.service.conta.ConviteService;
import br.com.vigentra.web.dto.AceiteConviteForm;
import br.com.vigentra.web.dto.ConviteForm;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Utilitários para montar cenários nos testes de integração. */
@Component
public class TestSupport {

    public static final String SENHA = "Senha@123";

    private final EmpresaService empresaService;
    private final UsuarioDetailsService uds;
    private final UsuarioRepository usuarioRepo;
    private final CategoriaRepository categoriaRepo;
    private final DocumentoService documentoService;
    private final UsuarioService usuarioService;
    private final ConviteService conviteService;

    public TestSupport(EmpresaService empresaService, UsuarioDetailsService uds, UsuarioRepository usuarioRepo,
                       CategoriaRepository categoriaRepo, DocumentoService documentoService, UsuarioService usuarioService,
                       ConviteService conviteService) {
        this.empresaService = empresaService;
        this.uds = uds;
        this.usuarioRepo = usuarioRepo;
        this.categoriaRepo = categoriaRepo;
        this.documentoService = documentoService;
        this.usuarioService = usuarioService;
        this.conviteService = conviteService;
    }

    public static String unico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Cadastra uma nova empresa e devolve o administrador criado. */
    public Usuario novaEmpresa(String nome) {
        RegistroForm f = new RegistroForm();
        f.setNome("Admin " + nome);
        f.setEmpresa(nome);
        f.setEmail(unico("admin") + "@teste.com");
        f.setSenha(SENHA);
        f.setConfirmacaoSenha(SENHA);
        f.setAceiteTermos(true);
        Usuario u = empresaService.registrar(f);
        // a maioria dos testes parte de uma conta já confirmada
        u.setEmailVerificadoEm(java.time.LocalDateTime.now());
        return usuarioRepo.save(u);
    }

    /** Cadastro SEM confirmar o e-mail (para testar a jornada de confirmação). */
    public Usuario novaEmpresaNaoConfirmada(String nome, String email) {
        RegistroForm f = new RegistroForm();
        f.setNome("Admin " + nome);
        f.setEmpresa(nome);
        f.setEmail(email);
        f.setSenha(SENHA);
        f.setConfirmacaoSenha(SENHA);
        f.setAceiteTermos(true);
        return empresaService.registrar(f);
    }

    public UsuarioPrincipal principal(Usuario u) {
        return uds.loadUserByUsername(u.getEmail());
    }

    /** Autentica o usuário no SecurityContext (para chamar serviços diretamente). */
    public UsuarioPrincipal logar(Usuario u) {
        UsuarioPrincipal p = principal(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return p;
    }

    public void deslogar() {
        SecurityContextHolder.clearContext();
    }

    public Categoria categoria() {
        return categoriaRepo.findAtivas(SecurityUtils.empresaId()).get(0);
    }

    /** Cria um documento (com o usuário logado) vencendo em "dias" (null = sem vencimento). */
    public Documento documento(String nome, Integer dias) {
        DocumentoForm f = new DocumentoForm();
        f.setNome(nome);
        f.setCategoriaId(categoria().getId());
        if (dias == null) f.setSemVencimento(true);
        else f.setDataVencimento(documentoService.hoje().plusDays(dias));
        f.setResponsavelNome("Resp " + nome);
        f.setResponsavelEmail(unico("resp") + "@teste.com");
        return documentoService.salvar(f, null);
    }

    /** Convida (com o usuário logado, que deve ter USER_MANAGE) e aceita o convite: novo usuário na mesma empresa. */
    public Usuario usuario(PerfilCodigo perfil) {
        ConviteForm c = new ConviteForm();
        c.setEmail(unico(perfil.name().toLowerCase()) + "@teste.com");
        c.setPerfil(perfil);
        c.setResponsavel(true);
        var criado = conviteService.convidar(c);
        AceiteConviteForm a = new AceiteConviteForm();
        a.setToken(criado.token());
        a.setNome("Usuário " + perfil.getNome());
        a.setSenha(SENHA);
        a.setConfirmacaoSenha(SENHA);
        a.setAceiteTermos(true);
        return conviteService.aceitar(a);
    }

    public Usuario recarregar(Usuario u) {
        return usuarioRepo.findById(u.getId()).orElseThrow();
    }

    public Usuario salvar(Usuario u) {
        return usuarioRepo.save(u);
    }
}
