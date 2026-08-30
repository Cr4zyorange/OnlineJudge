package com.onlinejudge.courseservice.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Reconciles deployments predating the broker consumer with a complete, atomic roster fact. */
@Component
public class CourseBootstrapPublisher {
    private final CourseService service;
    public CourseBootstrapPublisher(CourseService service) { this.service = service; }
    @EventListener(ApplicationReadyEvent.class)
    public void publishSnapshots() { service.publishBootstrapSnapshots(); }
}
