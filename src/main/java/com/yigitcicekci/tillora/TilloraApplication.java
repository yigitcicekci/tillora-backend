package com.yigitcicekci.tillora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class TilloraApplication {

    public static void main(String[] args) {
        SpringApplication.run(TilloraApplication.class, args);
    }
}
