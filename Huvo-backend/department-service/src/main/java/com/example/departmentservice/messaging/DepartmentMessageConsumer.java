package com.example.departmentservice.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DepartmentMessageConsumer {
    @RabbitListener(queues = "employee.queue")
    public void processEmployeeUpdate(String message) {
        System.out.println("Received: " + message);
    }
}
//Testing Webookgit