package br.com.sgsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SgsmApplication {

	public static void main(String[] args) {
		SpringApplication.run(SgsmApplication.class, args);
	}

}
