package com.opsconsole.tester.controller;

import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.CurrentUser;
import com.opsconsole.auth.service.NavAccessService;
import com.opsconsole.tester.domain.BajajEnvironment;
import com.opsconsole.tester.dto.BajajInvokeRequest;
import com.opsconsole.tester.dto.BajajInvokeResponse;
import com.opsconsole.tester.dto.OperationListResponseDto;
import com.opsconsole.tester.dto.TokenStatusDto;
import com.opsconsole.tester.exception.BajajTesterException;
import com.opsconsole.tester.service.BajajApiInvokeService;
import com.opsconsole.tester.service.BajajOperationListService;
import com.opsconsole.tester.service.BajajTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.opsconsole.common.dto.ErrorResponse;

@RestController
@RequestMapping("/api/tester")
public class TesterApiController {

    private final BajajOperationListService operationListService;
    private final BajajApiInvokeService invokeService;
    private final BajajTokenService tokenService;
    private final NavAccessService navAccessService;

    public TesterApiController(
            BajajOperationListService operationListService,
            BajajApiInvokeService invokeService,
            BajajTokenService tokenService,
            NavAccessService navAccessService
    ) {
        this.operationListService = operationListService;
        this.invokeService = invokeService;
        this.tokenService = tokenService;
        this.navAccessService = navAccessService;
    }

    @GetMapping("/operations")
    public OperationListResponseDto operations(@RequestParam(defaultValue = "UAT") String environment) {
        requireTesterAccess();
        BajajEnvironment env = parseEnvironment(environment);

        // Step 1: operation list. Step 2: oauth-token, reusing that list for its key/IV so the
        // token header is already warm by the time the user picks an API.
        OperationListResponseDto operations = operationListService.fetchOperations(env);
        return operations.withTokenStatus(tokenService.primeToken(env, operations.operations()));
    }

    @PostMapping("/invoke")
    public BajajInvokeResponse invoke(@RequestBody BajajInvokeRequest request) {
        requireTesterAccess();
        return invokeService.invoke(request);
    }

    /** Forces a fresh oauth-token call; returns only a masked preview, never the raw token. */
    @PostMapping("/token/refresh")
    public TokenStatusDto refreshToken(@RequestParam(defaultValue = "UAT") String environment) {
        requireTesterAccess();
        BajajEnvironment env = parseEnvironment(environment);
        tokenService.invalidate(env);
        tokenService.refreshToken(env);
        return tokenService.status(env);
    }

    @ExceptionHandler(BajajTesterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBajajError(BajajTesterException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    private static BajajEnvironment parseEnvironment(String environment) {
        if (environment != null && environment.equalsIgnoreCase("PROD")) {
            return BajajEnvironment.PROD;
        }
        if (environment != null && environment.equalsIgnoreCase("UAT")) {
            return BajajEnvironment.UAT;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Environment must be UAT or PROD");
    }

    private void requireTesterAccess() {
        AppUser user = CurrentUser.requireUser();
        if (!navAccessService.canAccess(user, AppTab.TESTER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tester access required");
        }
    }
}