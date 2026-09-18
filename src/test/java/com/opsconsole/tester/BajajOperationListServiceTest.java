package com.opsconsole.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsconsole.tester.config.BajajTesterProperties;
import com.opsconsole.tester.domain.BajajEnvironment;
import com.opsconsole.tester.exception.BajajTesterException;
import com.opsconsole.tester.service.BajajOperationListService;
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
