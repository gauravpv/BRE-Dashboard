package com.bredashboard.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bredashboard.tester.config.BajajTesterProperties;
import com.bredashboard.tester.domain.BajajEnvironment;
import com.bredashboard.tester.exception.BajajTesterException;
import com.bredashboard.tester.service.BajajOperationListService;
import com.bredashboard.tester.service.BajajTokenService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BajajTokenServiceTest {

    private static BajajTokenService newService(BajajTesterProperties properties) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new BajajTokenService(
                properties,
                new BajajOperationListService(properties, objectMapper),
                objectMapper
        );
    }

    @Test
    void refreshToken_failsWhenOperationListUnavailable() {
        // No encryption key/IV configured, so the oauth-token lookup cannot complete.
        BajajTokenService service = newService(new BajajTesterProperties());

        assertThatThrownBy(() -> service.refreshToken(BajajEnvironment.UAT))
                .isInstanceOf(BajajTesterException.class);
    }

    @Test
    void findOperation_rejectsBlankName() {
        BajajTesterProperties properties = new BajajTesterProperties();
        BajajOperationListService service =
                new BajajOperationListService(properties, new ObjectMapper());

        assertThatThrownBy(() -> service.findOperation(BajajEnvironment.UAT, "  "))
                .isInstanceOf(BajajTesterException.class)
                .hasMessageContaining("Operation name is required");
    }
}
