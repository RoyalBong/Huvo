package com.huvo.identity.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One topic exchange per bounded context (Huvo_Backend_Context.md Section 7).
 *
 * <p>identity-service publishes {@code employee.created} and {@code employee.updated} on
 * {@value #IDENTITY_EXCHANGE}; consumers bind their own queues to it. Nothing else about
 * messaging is centralised - there is deliberately no broker-level "smart routing".
 */
@Configuration
public class RabbitConfig {

    /** Topic exchange for everything this service publishes. */
    public static final String IDENTITY_EXCHANGE = "identity.exchange";

    /**
     * Queue the department domain binds to the employee event stream. Each consumer owns
     * its own queue binding - publishers never know who listens (Section 7).
     */
    public static final String DEPARTMENT_EMPLOYEE_SYNC_QUEUE = "department.employee-sync";

    @Bean
    public TopicExchange identityExchange() {
        // durable (survives a broker restart), non-auto-delete (declared topology is stable)
        return new TopicExchange(IDENTITY_EXCHANGE, true, false);
    }

    @Bean
    public Queue departmentEmployeeSyncQueue() {
        return new Queue(DEPARTMENT_EMPLOYEE_SYNC_QUEUE, true);
    }

    @Bean
    public Binding departmentEmployeeSyncBinding() {
        return BindingBuilder.bind(departmentEmployeeSyncQueue())
                .to(identityExchange())
                .with("employee.#");
    }
}
