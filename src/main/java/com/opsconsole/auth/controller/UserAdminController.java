package com.opsconsole.auth.controller;

import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.auth.service.RoleAdminService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserAdminController {

    private final RoleAdminService roleAdminService;

    public UserAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping("/users")
    public String userManagement(Model model) {
        model.addAttribute("activeNav", AppTab.USERS.id());
        model.addAttribute("users", roleAdminService.allUsers());
        model.addAttribute("roles", roleAdminService.allRoles());
        model.addAttribute("roleTabFlags", roleAdminService.tabMatrixForAllRoles());
        model.addAttribute("allTabs", AppTab.values());
        return "user-management";
    }
}
