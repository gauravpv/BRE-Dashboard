package com.opsconsole.tester.controller;

import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.CurrentUser;
import com.opsconsole.auth.service.NavAccessService;
import com.opsconsole.common.dto.ErrorResponse;
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
    public OperationListResponseDto operations(
            @RequestParam(defaultValue = "UAT") String environment,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        requireTesterAccess();
        BajajEnvironment env = requireEnvironment(environment);
        if (refresh) {
            tokenService.invalidate(env);
        }
        OperationListResponseDto operations = operationListService.fetchOperations(env, refresh);
        return operations.withTokenStatus(tokenService.primeToken(env, operations.operations()));
    }

    @PostMapping("/invoke")
    public BajajInvokeResponse invoke(@RequestBody BajajInvokeRequest request) {
        requireTesterAccess();
        return invokeService.invoke(request);
    }

    @PostMapping("/token/refresh")
    public TokenStatusDto refreshToken(@RequestParam(defaultValue = "UAT") String environment) {
        requireTesterAccess();
        BajajEnvironment env = requireEnvironment(environment);
        tokenService.refreshToken(env);
        return tokenService.status(env);
    }

    @ExceptionHandler(BajajTesterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBajajError(BajajTesterException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    private static BajajEnvironment requireEnvironment(String environment) {
        BajajEnvironment parsed = BajajEnvironment.parse(environment);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Environment must be UAT or PROD");
        }
        return parsed;
    }

    private void requireTesterAccess() {
        AppUser user = CurrentUser.requireUser();
        if (!navAccessService.canAccess(user, AppTab.TESTER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tester access required");
        }
    }
}
