package com.force.miniserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceController {

    private final Logger logger = LoggerFactory.getLogger(DeviceController.class);

    @Autowired
    private DeviceService deviceService;

    @GetMapping("/greetings")
    public String greetings(String message) {
        logger.info("greetings {}", message);
        deviceService.stopScreen();
        return "hello: " + message;
    }
}
