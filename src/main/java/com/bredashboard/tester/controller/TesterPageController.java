package com.bredashboard.tester.controller;

import com.bredashboard.auth.domain.AppTab;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TesterPageController {

    @GetMapping("/tester")
    public String testerPage(Model model) {
        model.addAttribute("activeNav", AppTab.TESTER.id());
        return "tester";
    }
}
