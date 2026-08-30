package com.onlinejudge.courseservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("course.rabbit")
public class CourseRabbitProperties {
    private boolean enabled;
    private String host = "localhost";
    private int port = 5672;
    private String username = "guest";
    private String password = "guest";
    // D6-MSG owns the shared durable topology.  Course must not invent a
    // second exchange or consumers will correctly treat its outbox as
    // unroutable instead of silently missing membership facts.
    private String exchange = "onlinejudge.events.v2";
    private String identitySecurityVersionQueue = "course.identity-security-version.v2";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getIdentitySecurityVersionQueue() { return identitySecurityVersionQueue; }
    public void setIdentitySecurityVersionQueue(String identitySecurityVersionQueue) { this.identitySecurityVersionQueue = identitySecurityVersionQueue; }
}
