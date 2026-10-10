package com.bredashboard.tester.controller;

import com.bredashboard.auth.domain.AppTab;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.CurrentUser;
import com.bredashboard.auth.service.NavAccessService;
import com.bredashboard.common.dto.ErrorResponse;
import com.bredashboard.health.config.HealthProperties;
import com.bredashboard.tester.domain.BajajEnvironment;
import com.bredashboard.tester.dto.BajajInvokeRequest;
import com.bredashboard.tester.dto.BajajInvokeResponse;
import com.bredashboard.tester.dto.OperationListResponseDto;
import com.bredashboard.tester.dto.TokenStatusDto;
import com.bredashboard.tester.exception.BajajTesterException;
import com.bredashboard.tester.service.BajajApiInvokeService;
import com.bredashboard.tester.service.BajajOperationListService;
import com.bredashboard.tester.service.BajajTokenService;
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
    private final HealthProperties healthProperties;

    public TesterApiController(
            BajajOperationListService operationListService,
            BajajApiInvokeService invokeService,
            BajajTokenService tokenService,
            NavAccessService navAccessService,
            HealthProperties healthProperties
    ) {
        this.operationListService = operationListService;
        this.invokeService = invokeService;
        this.tokenService = tokenService;
        this.navAccessService = navAccessService;
        this.healthProperties = healthProperties;
    }

    @GetMapping("/operations")
    public OperationListResponseDto operations(
            @RequestParam(required = false) String environment,
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
        BajajEnvironment environment = requireEnvironment(request.environment());
        return invokeService.invoke(new BajajInvokeRequest(
            environment.name(), request.publicUrl(), request.encryptionKey(), request.encryptionIv(), request.requestBody()));
    }

    @PostMapping("/token/refresh")
    public TokenStatusDto refreshToken(@RequestParam(required = false) String environment) {
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

    private BajajEnvironment requireEnvironment(String environment) {
        BajajEnvironment configured = BajajEnvironment.valueOf(
                healthProperties.getHealth().deploymentTier().name());
        BajajEnvironment parsed = environment == null || environment.isBlank()
                ? configured : BajajEnvironment.parse(environment);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Environment must be UAT or PROD");
        }
        if (parsed != configured) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This deployment is configured for " + configured + " only");
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
