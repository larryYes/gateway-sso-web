package com.tyymt.wxplatform.gateway.sso.web.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.tyymt.wxplatform.gateway.sso.web.constant.CommonConstants;
import com.tyymt.wxplatform.gateway.sso.web.constant.ResultConstants;
import com.tyymt.wxplatform.gateway.sso.web.constant.ResultEnum;
import com.tyymt.wxplatform.gateway.sso.web.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import net.bestjoy.cloud.core.bean.Result;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author huangfei
 * @create 2020-10-26 14:14
 */
@Component
@Slf4j
public class CustomFilter implements GlobalFilter, Ordered {

    @Autowired
    AuthService authService;

    @Value("${gateway.redirect.loginUrl}")
    private String loginUrl;

    @Value("${gateway.ignore.authentication.currentUser}")
    private String currentUserUrl;

    @Value("${gateway.ignore.authentication.logout}")
    private String logoutUrl;

    @Value("${gateway.cookie.domain}")
    private String domain;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    private final Base64.Encoder encoder = Base64.getEncoder();

    private final static ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authentication = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String method = request.getMethodValue();
        String serviceName = getServiceName(exchange, chain);
        HttpCookie httpCookie = request.getCookies().getFirst(CommonConstants.COOKIE_RETURN);
        String userToken = "";
        if (httpCookie != null) {
            userToken = httpCookie.getValue();
        }

        String url = request.getPath().value();
        log.info("serviceName:{},url:{},method:{}", serviceName, url, method);

        try {
            if (authService.ignoreAuthentication(url)) {
                return setResponseBody(exchange, chain);
            }

            if (!StringUtils.isEmpty(authentication) || !StringUtils.isEmpty(userToken)) {
                String token = StringUtils.isEmpty(authentication) ? userToken : authentication;
                //校验token是否失效并刷新token
                if (!tokenRfresh(token)) {
                    return returnError(exchange);
                }
                if (url.startsWith(logoutUrl)) {
                    return logout(exchange, token);
                }
                //将userInfo信息添加到请求头,移除其中的system信息
                ServerHttpRequest host = request.mutate().headers(httpHeaders -> {
                    try {
                        String userInfo = stringRedisTemplate.opsForValue().get(CommonConstants.USER_INFO_KEY + token);
                        JSONObject jsonObject = JSON.parseObject(userInfo);
                        if (jsonObject.containsKey(ResultConstants.SYSTEM_LIST)) {
                            jsonObject.remove(ResultConstants.SYSTEM_LIST);
                        }
                        httpHeaders.add(ResultConstants.USER_INFO, encoder.encodeToString(jsonObject.toJSONString().getBytes("UTF-8")));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }).build();
                return chain.filter(exchange.mutate().request(host).build());

            } else {
                return returnError(exchange);
            }
        } catch (Exception ex) {
            log.error("gateway异常：", ex);
            return returnVo(exchange, ResultEnum.GATEWAY_ERROR.getCode(), ResultEnum.GATEWAY_ERROR.getMessage());
        }
    }

    private Mono<Void> returnError(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> maps = new HashMap<>(16);
        maps.put("message", "用户未登录");
        maps.put("loginUrl", loginUrl);
        String fastResult = JSON.toJSONString(maps);
        DataBuffer dataBuffer = response.bufferFactory().allocateBuffer().write(fastResult.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(dataBuffer));
    }

    /**
     * 修改response返回值
     *
     * @param exchange
     * @param chain
     * @return
     */
    private Mono<Void> setResponseBody(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        ByteOutputStream outputStream = new ByteOutputStream();
                        dataBuffers.forEach(i -> {
                            byte[] array = new byte[i.readableByteCount()];
                            i.read(array);
                            outputStream.write(array);
                        });

                        //修改response之后的字符串
                        String lastStr = new String(outputStream.getBytes(), Charset.forName("UTF-8"));
                        //修改response的值
                        lastStr = JSONObject.toJSONString(getGlobalResponse(originalResponse, lastStr));

                        byte[] uppedContent = lastStr.getBytes();
                        return bufferFactory.wrap(uppedContent);
                    }));
                }
                // if body is not a flux. never got there.
                return super.writeWith(body);
            }
        };
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 校验token是否存在，如果存在重新设置token失效时间
     *
     * @param token
     * @return
     */
    private Boolean tokenRfresh(String token) {
        String key = CommonConstants.USER_INFO_KEY + token;
        if (stringRedisTemplate.hasKey(key)) {
            stringRedisTemplate.expire(key, 60 * 60 * 12, TimeUnit.SECONDS);
            return true;
        } else {
            return false;
        }

    }

    /**
     * 返回值封装
     *
     * @param serverWebExchange
     * @param code
     * @param message
     * @return
     */
    private Mono<Void> returnVo(ServerWebExchange serverWebExchange, int code, String message) {
        Result respResultVO = Result.fail(code, message);
        ServerHttpResponse response = serverWebExchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        HttpHeaders header = response.getHeaders();
        header.add("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = new byte[0];
        try {
            bytes = objectMapper.writeValueAsBytes(respResultVO);
        } catch (JsonProcessingException e) {
            log.error("json转化异常", e);
        }
        DataBuffer buffer = serverWebExchange.getResponse().bufferFactory().wrap(bytes);
        return serverWebExchange.getResponse().writeWith(Flux.just(buffer));

    }

    private String getServiceName(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route gatewayUrl = exchange.getRequiredAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI uri = gatewayUrl.getUri();
        return uri.getHost();

    }

    @Override
    public int getOrder() {
        return -2;
    }

    private Object getGlobalResponse(ServerHttpResponse originalResponse, String originalResponseStr) {
        log.info("原始Response:{}", originalResponseStr);
        JSONObject jsonObject = JSON.parseObject(originalResponseStr);
        if (jsonObject.containsKey(ResultConstants.CODE)) {
            if (jsonObject.getInteger(ResultConstants.CODE).equals(ResultConstants.SUCCESS)) {
                if (jsonObject.containsKey(ResultConstants.RESULT)) {
                    JSONObject returnVo = JSON.parseObject(jsonObject.getString(ResultConstants.RESULT));
                    //通过uuid生成token
                    final String token = UUID.randomUUID().toString();
                    String userInfo = returnVo.getString(ResultConstants.USER_INFO);
                    setToken(token, userInfo);

                    //返回cookie
                    originalResponse.addCookie(ResponseCookie.from(CommonConstants.COOKIE_RETURN, token).domain(domain).path("/").build());

                    //移除系统信息
                    JSONObject sysList = JSON.parseObject(userInfo);
                    sysList.remove(ResultConstants.SYSTEM_LIST);
                    returnVo.put(ResultConstants.USER_INFO, sysList);
                    returnVo.put("token", token);
                    jsonObject.put(ResultConstants.RESULT, returnVo);
                }
            }
        }

        return jsonObject;
    }

    /**
     * 将用户信息存入redis
     *
     * @param token    生成的token
     * @param userInfo 用户信息
     */
    public void setToken(String token, String userInfo) {
        String key = CommonConstants.USER_INFO_KEY + token;
        stringRedisTemplate.opsForValue().set(key, userInfo, 60 * 60 * 12, TimeUnit.SECONDS);
    }

    /**
     * 获取当前用户信息
     *
     * @param exchange
     * @param token
     * @return
     */
    public Mono<Void> getUser(ServerWebExchange exchange, String token) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String fastResult = JSON.toJSONString(Result.success(JSONObject.parseObject(stringRedisTemplate.opsForValue().get(CommonConstants.USER_INFO_KEY + token))));
        DataBuffer dataBuffer = response.bufferFactory().allocateBuffer().write(fastResult.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(dataBuffer));
    }

    /**
     * 当前用户退出登录
     *
     * @param exchange
     * @param token
     * @return
     */
    public Mono<Void> logout(ServerWebExchange exchange, String token) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        stringRedisTemplate.delete(CommonConstants.USER_INFO_KEY + token);
        String fastResult = JSON.toJSONString(Result.success());
        DataBuffer dataBuffer = response.bufferFactory().allocateBuffer().write(fastResult.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(dataBuffer));
    }
}
