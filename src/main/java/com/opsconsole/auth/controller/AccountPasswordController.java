package com.opsconsole.auth.controller;

import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.CurrentUser;
import com.opsconsole.auth.service.RoleAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountPasswordController {

    private final RoleAdminService roleAdminService;

    public AccountPasswordController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping("/account/password")
    public String form(Model model) {
        model.addAttribute("activeNav", "");
        return "account-password";
    }

    @PostMapping("/account/password")
    public String change(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes
    ) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New password and confirmation do not match.");
            return "redirect:/account/password";
        }
        try {
            AppUser actor = CurrentUser.requireUser();
            roleAdminService.changeOwnPassword(actor, currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("success", "Password updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/password";
    }
}
