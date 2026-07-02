package com.sparta.copa.copapayment.common.logging;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 컨트롤러·서비스 계층 메서드의 진입/종료/소요시간을 공통으로 로깅한다.
 * traceId/spanId는 Micrometer Tracing이 MDC에 넣어 주므로 로그 패턴/JSON 인코더가 함께 남긴다.
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void controllerLayer() {
    }

    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void serviceLayer() {
    }

    @Around("controllerLayer() || serviceLayer()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String type = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String method = joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();

        if (log.isDebugEnabled()) {
            log.debug("→ {}.{} args={}", type, method, Arrays.toString(joinPoint.getArgs()));
        }
        try {
            Object result = joinPoint.proceed();
            log.info("{}.{} 완료 ({}ms)", type, method, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            log.error("{}.{} 실패 ({}ms): {}", type, method, System.currentTimeMillis() - start, ex.toString());
            throw ex;
        }
    }
}