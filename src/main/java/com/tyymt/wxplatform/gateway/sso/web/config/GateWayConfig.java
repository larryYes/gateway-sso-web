package com.tyymt.wxplatform.gateway.sso.web.config;

import com.alibaba.fastjson.JSONObject;
import com.tyymt.wxplatform.gateway.sso.web.GateWayVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

/**
 * @author huangfei
 * @create 2020-11-23
 */
@Configuration
public class GateWayConfig {
    @Autowired
    RedisTemplate redisTemplate;

    /**
     * 配置一个id为route-name的路由规则，
     * 当访问地址http://localhost:9527/guonei时会自动转发到地址：http://news.baidu.com/guonei
     *
     * @param routeLocatorBuilder
     * @return
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder routeLocatorBuilder) {
        Map<String, String> entries = redisTemplate.opsForHash().entries("gateway:rule:key");
        RouteLocatorBuilder.Builder routes = routeLocatorBuilder.routes();
        entries.forEach((k, v) -> {
            GateWayVo gateWayVo = JSONObject.parseObject(v, GateWayVo.class);
            routes.route(gateWayVo.getId(), r -> r.path(gateWayVo.getPath()).uri(gateWayVo.getUri())).build();
        });
        return routes.build();
    }
}
