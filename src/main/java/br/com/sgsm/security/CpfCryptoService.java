package br.com.sgsm.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Criptografia em repouso de campos sensíveis (CPF) + índice cego para busca de
 * duplicidade sem decifrar. AES-256-GCM é não-determinístico (IV aleatório por
 * chamada) — por isso a unicidade de CPF passa a ser garantida via {@code cpfHash}
 * (HMAC-SHA256, determinístico), nunca comparando o valor cifrado diretamente.
 */
@Service
public class CpfCryptoService {

    private static final String PREFIX = "v1:";
    private static final String AES_TRANSFORMACAO = "AES/GCM/NoPadding";
    private static final int GCM_IV_TAMANHO = 12;
    private static final int GCM_TAG_TAMANHO_BITS = 128;

    private final SecretKeySpec chaveAes;
    private final SecretKeySpec chaveHmac;

    public CpfCryptoService(
            @Value("${sgsm.crypto.cpf-key}") String chaveAesBase64,
            @Value("${sgsm.crypto.cpf-hmac-key}") String chaveHmacBase64) {
        this.chaveAes = new SecretKeySpec(Base64.getDecoder().decode(chaveAesBase64), "AES");
        this.chaveHmac = new SecretKeySpec(Base64.getDecoder().decode(chaveHmacBase64), "HmacSHA256");
    }

    public String encrypt(String textoPuro) {
        if (textoPuro == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_TAMANHO];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMACAO);
            cipher.init(Cipher.ENCRYPT_MODE, chaveAes, new GCMParameterSpec(GCM_TAG_TAMANHO_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPuro.getBytes(StandardCharsets.UTF_8));

            byte[] combinado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, combinado, 0, iv.length);
            System.arraycopy(cifrado, 0, combinado, iv.length, cifrado.length);
            return PREFIX + Base64.getEncoder().encodeToString(combinado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao criptografar dado sensível", e);
        }
    }

    /**
     * Valores sem o prefixo "v1:" são tratados como legado (texto puro, gravado antes
     * desta feature) e retornados sem alteração — permite convivência transitória até
     * o backfill (CpfBackfillRunner) reescrever todas as linhas antigas.
     */
    public String decrypt(String valorArmazenado) {
        if (valorArmazenado == null) return null;
        if (!isEncrypted(valorArmazenado)) return valorArmazenado;
        try {
            byte[] combinado = Base64.getDecoder().decode(valorArmazenado.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(combinado, 0, GCM_IV_TAMANHO);
            byte[] cifrado = Arrays.copyOfRange(combinado, GCM_IV_TAMANHO, combinado.length);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMACAO);
            cipher.init(Cipher.DECRYPT_MODE, chaveAes, new GCMParameterSpec(GCM_TAG_TAMANHO_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decriptografar dado sensível", e);
        }
    }

    public boolean isEncrypted(String valorArmazenado) {
        return valorArmazenado != null && valorArmazenado.startsWith(PREFIX);
    }

    /** Índice cego determinístico — nunca use o valor cifrado (encrypt) para buscar duplicidade. */
    public String hash(String textoPuro) {
        if (textoPuro == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveHmac);
            byte[] digest = mac.doFinal(textoPuro.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao gerar hash", e);
        }
    }
}
