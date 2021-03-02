package com.tyymt.wxplatform.gateway.sso.web.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.stream.Stream;

/**
 * @author huangfei
 * @create 2020-10-26
 */
@Service
@Slf4j
public class AuthService {

    @Value("${gateway.ignore.authentication.startWith}")
    private String ignoreUrls;

    public boolean ignoreAuthentication(String url) {
        return Stream.of(this.ignoreUrls.split(",")).anyMatch(ignoreUrl -> url.startsWith(StringUtils.trim(ignoreUrl)));
    }

}
