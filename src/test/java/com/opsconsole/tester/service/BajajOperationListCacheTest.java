package com.opsconsole.tester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsconsole.tester.config.BajajTesterProperties;
import com.opsconsole.tester.domain.BajajEnvironment;
import com.opsconsole.tester.dto.OperationEntryDto;
import com.opsconsole.tester.dto.OperationListResponseDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BajajOperationListCacheTest {

    @Test
    void fetchOperations_reusesCachedListUntilExpiry() {
        BajajOperationListService service =
                new BajajOperationListService(new BajajTesterProperties(), new ObjectMapper());
        OperationListResponseDto cached = sampleList();
        service.putCache(BajajEnvironment.UAT, cached, Instant.now().plusSeconds(3_600));

        OperationListResponseDto first = service.fetchOperations(BajajEnvironment.UAT);
        OperationListResponseDto second = service.fetchOperations(BajajEnvironment.UAT, false);

        assertThat(first.fromCache()).isTrue();
        assertThat(second.fromCache()).isTrue();
        assertThat(first.operations()).hasSize(1);
        assertThat(first.operations().get(0).publicUrl()).isEqualTo("oauth-token");
        assertThat(first.listExpiresInSeconds()).isPositive();
    }

    @Test
    void findOperation_usesCachedListInsteadOfLiveCall() {
        BajajOperationListService service =
                new BajajOperationListService(new BajajTesterProperties(), new ObjectMapper());
        service.putCache(BajajEnvironment.UAT, sampleList(), Instant.now().plusSeconds(3_600));

        OperationEntryDto found = service.findOperation(BajajEnvironment.UAT, "oauth-token");

        assertThat(found.encryptionKey()).isEqualTo("hash");
        assertThat(found.encryptionIv()).isEqualTo("salt");
    }

    private static OperationListResponseDto sampleList() {
        return new OperationListResponseDto(
                "UAT",
                "https://sauat.bajajfinserv.in/apis",
                "ok",
                "200",
                List.of(new OperationEntryDto(
                        "oauth-token",
                        "oauth-token",
                        "1",
                        "auth",
                        "1",
                        "https://sauat.bajajfinserv.in/apis/oauth-token",
                        "hash",
                        "salt"
                )),
                null,
                false,
                null
        );
    }
}
