package com.tyymt.wxplatform.gateway.sso.web;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;

/**
 * 启动类
 *
 * @author huangfei
 * @create 2020-10-14 15:58
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@Slf4j
public class GatewaySsoWebApplication {

    @SneakyThrows
    public static void main(String[] args) {
        ConfigurableApplicationContext application = SpringApplication.run(GatewaySsoWebApplication.class, args);

        Environment env = application.getEnvironment();
        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("server.port");
        String path = env.getProperty("server.servlet.context-path");
        String serviceName = env.getProperty("spring.application.name");
        log.info("\n----------------------------------------------------------\n\t" +
                "Service:" + serviceName + " is running! Access URLs:\n\t" +
                "Local: \t\thttp://localhost:" + port + path + "/\n\t" +
                "External: \thttp://" + ip + ":" + port + path + "/\n\t" +
                "swagger-ui: http://" + ip + ":" + port + path + "/swagger-ui.html\n" +
                "----------------------------------------------------------");
    }

}
