package com.yigitcicekci.tillora;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularArchitectureTest {

    @Test
    void verifiesModularBoundaries() {
        ApplicationModules.of(TilloraApplication.class).verify();
    }
}
