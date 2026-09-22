package com.bredashboard.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bredashboard.tester.config.BajajTesterProperties;
import com.bredashboard.tester.domain.BajajEnvironment;
import com.bredashboard.tester.exception.BajajTesterException;
import com.bredashboard.tester.service.BajajOperationListService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BajajOperationListServiceTest {

    @Test
    void fetchOperations_withoutEncryptionKeys_fails() {
        BajajOperationListService service = new BajajOperationListService(new BajajTesterProperties(), new ObjectMapper());

        assertThatThrownBy(() -> service.fetchOperations(BajajEnvironment.UAT))
                .isInstanceOf(BajajTesterException.class);
    }
}
