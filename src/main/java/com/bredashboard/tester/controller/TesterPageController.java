package com.bredashboard.tester.controller;

import com.bredashboard.auth.domain.AppTab;
import com.bredashboard.health.config.HealthProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TesterPageController {

    private final HealthProperties healthProperties;

    public TesterPageController(HealthProperties healthProperties) {
        this.healthProperties = healthProperties;
    }

    @GetMapping("/tester")
    public String testerPage(Model model) {
        model.addAttribute("activeNav", AppTab.TESTER.id());
        model.addAttribute("deploymentTier", healthProperties.getHealth().deploymentTier());
        return "tester";
    }
}
