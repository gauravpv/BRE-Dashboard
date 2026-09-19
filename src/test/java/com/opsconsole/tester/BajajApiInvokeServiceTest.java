package com.opsconsole.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsconsole.tester.config.BajajTesterProperties;
import com.opsconsole.tester.dto.BajajInvokeRequest;
import com.opsconsole.tester.exception.BajajTesterException;
import com.opsconsole.tester.service.BajajApiInvokeService;
import com.opsconsole.tester.service.BajajOperationListService;
import com.opsconsole.tester.service.BajajTokenService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BajajApiInvokeServiceTest {

    /**
     * Validation fails before any token/network call, so the collaborators are never exercised.
     */
    private static BajajApiInvokeService newService() {
        BajajTesterProperties properties = new BajajTesterProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        BajajTokenService tokenService = new BajajTokenService(
                properties,
                new BajajOperationListService(properties, objectMapper),
                objectMapper
        );
        return new BajajApiInvokeService(properties, tokenService, objectMapper);
    }

    @Test
    void invoke_rejectsMissingEncryptionMaterial() {
        BajajApiInvokeService service = newService();

        BajajInvokeRequest request = new BajajInvokeRequest(
                "UAT",
                "authbre/authorization",
                "",
                "",
                "{\"mobile\":\"9999999999\"}"
        );

        assertThatThrownBy(() -> service.invoke(request))
                .isInstanceOf(BajajTesterException.class)
                .hasMessageContaining("Encryption key");
    }

    @Test
    void invoke_rejectsInvalidJson() {
        BajajApiInvokeService service = newService();

        BajajInvokeRequest request = new BajajInvokeRequest(
                "UAT",
                "authbre/authorization",
                "19LPRFUYTVERETJY",
                "S5AFOYRNUNZENGJCEQ1W81DJB55QK76M",
                "{not-json"
        );

        assertThatThrownBy(() -> service.invoke(request))
                .isInstanceOf(BajajTesterException.class)
                .hasMessageContaining("valid JSON");
    }
}