package br.com.sgsm.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Converter JPA — transparente para o restante da aplicação: {@code Paciente.getCpf()}
 * sempre retorna o texto puro, só o valor persistido no Postgres fica cifrado.
 * Registrado como @Component (não autoApply) para o Spring injetar o CpfCryptoService
 * via o bean container do Hibernate configurado automaticamente pelo Spring Boot.
 */
@Converter
@Component
public class CpfCryptoConverter implements AttributeConverter<String, String> {

    private final CpfCryptoService cryptoService;

    public CpfCryptoConverter(CpfCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cryptoService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cryptoService.decrypt(dbData);
    }
}
