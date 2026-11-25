package br.com.vigentra.web.dto;

/** Política de senha: mínimo de 8 caracteres, com letra maiúscula, minúscula e número. */
public final class SenhaPolitica {
    private SenhaPolitica() {}

    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$";
    public static final String MENSAGEM =
            "A senha deve ter de 8 a 72 caracteres, com letras maiúsculas, minúsculas e números.";
}
