package com.yigitcicekci.tillora.platformadmin.application.service;

import com.yigitcicekci.tillora.platformadmin.api.response.PlatformAdminSystemHealthResponse;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminSystemHealthService {

    public PlatformAdminSystemHealthResponse get() {
        return new PlatformAdminSystemHealthResponse("UP");
    }
}
