package com.opsconsole.auth.controller;

import com.opsconsole.auth.config.AuthProperties;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
@Controller
public class LoginController {

    private final AuthProperties authProperties;

    public LoginController(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @GetMapping("/login")
    public String login(
            Model model,
            CsrfToken csrfToken,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String pending,
            @RequestParam(required = false) String inactive,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String logout
    ) {
        if (csrfToken != null) {
            model.addAttribute("_csrf", csrfToken);
        }
        model.addAttribute("azureMode", authProperties.isAzureMode());
        model.addAttribute("devMode", authProperties.isDevMode());
        model.addAttribute("microsoftLogin", authProperties.isAzureOAuthConfigured());
        model.addAttribute("loginError", error != null);
        model.addAttribute("accessPending", pending != null);
        model.addAttribute("accountInactive", inactive != null);
        model.addAttribute("sessionExpired", expired != null);
        model.addAttribute("logoutSuccess", logout != null);
        return "login";
    }
}
