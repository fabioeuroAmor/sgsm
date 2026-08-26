package br.com.sgsm.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class CpfCryptoServiceTest {

    private CpfCryptoService service;

    @BeforeEach
    void setUp() {
        String chaveAes = Base64.getEncoder().encodeToString("chave-teste-de-32-bytes-exatos!!".getBytes());
        String chaveHmac = Base64.getEncoder().encodeToString("outra-chave-de-32-bytes-teste!!!".getBytes());
        service = new CpfCryptoService(chaveAes, chaveHmac);
    }

    @Test
    void deveDecifrarParaOMesmoValorOriginal() {
        String cifrado = service.encrypt("12345678900");

        assertThat(service.decrypt(cifrado)).isEqualTo("12345678900");
    }

    @Test
    void cifraDeveSerNaoDeterministica() {
        String cifrado1 = service.encrypt("12345678900");
        String cifrado2 = service.encrypt("12345678900");

        assertThat(cifrado1).isNotEqualTo(cifrado2);
        assertThat(service.decrypt(cifrado1)).isEqualTo(service.decrypt(cifrado2));
    }

    @Test
    void valorCifradoDeveComecarComPrefixoDeVersao() {
        String cifrado = service.encrypt("12345678900");

        assertThat(cifrado).startsWith("v1:");
        assertThat(service.isEncrypted(cifrado)).isTrue();
    }

    @Test
    void deveTratarValorSemPrefixoComoTextoPuroLegado() {
        assertThat(service.isEncrypted("12345678900")).isFalse();
        assertThat(service.decrypt("12345678900")).isEqualTo("12345678900");
    }

    @Test
    void hashDeveSerDeterministico() {
        assertThat(service.hash("12345678900")).isEqualTo(service.hash("12345678900"));
    }

    @Test
    void hashesDeCpfsDiferentesDevemSerDiferentes() {
        assertThat(service.hash("12345678900")).isNotEqualTo(service.hash("98765432100"));
    }

    @Test
    void deveRetornarNuloParaEntradaNula() {
        assertThat(service.encrypt(null)).isNull();
        assertThat(service.decrypt(null)).isNull();
        assertThat(service.hash(null)).isNull();
    }

    @Test
    void naoDeveGerarCifrasRepetidasEmMuitasChamadas() {
        var vistos = new HashSet<String>();
        for (int i = 0; i < 50; i++) {
            vistos.add(service.encrypt("12345678900"));
        }
        assertThat(vistos).hasSize(50);
    }
}
