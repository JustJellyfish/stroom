/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.server;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import stroom.entity.shared.PermissionException;
import stroom.security.Insecure;
import stroom.security.Secured;
import stroom.security.SecurityContext;
import stroom.util.logging.StroomLogger;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class UserSecurityMethodInterceptor {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(UserSecurityMethodInterceptor.class);

    private static final Class<?>[] SECURITY_ANNOTATIONS = new Class<?>[]{Secured.class,
            Insecure.class};
    private final ThreadLocal<Boolean> checkTypeThreadLocal = new ThreadLocal<>();

    private final SecurityContext securityContext;

    @Inject
    UserSecurityMethodInterceptor(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Pointcut("@within(stroom.security.Secured)")
    public void hasAnnotationOnType() {
    }

    @Pointcut("@annotation(stroom.security.Secured)")
    public void hasAnnotationOnMethod() {
    }

    @Around("hasAnnotationOnType() || hasAnnotationOnMethod()")
    public Object secureMethod(final ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // Initiate current check type.
            Boolean currentCheckType = checkTypeThreadLocal.get();
            if (currentCheckType == null) {
                currentCheckType = Boolean.TRUE;
                checkTypeThreadLocal.set(currentCheckType);
            }

            // If we aren't currently checking anything then just proceed.
            if (Boolean.FALSE.equals(currentCheckType)) {
                return joinPoint.proceed();
            }

            final Annotation securityAnnotation = getSecurityAnnotation(joinPoint);

            // If authentication is not required to call the current method or the current user is an administrator then
            // don't do any security checking.
            if (securityAnnotation == null || Insecure.class.isAssignableFrom(securityAnnotation.getClass()) || securityContext.isAdmin()) {
                try {
                    // Don't check any further permissions.
                    checkTypeThreadLocal.set(Boolean.FALSE);
                    return joinPoint.proceed();
                } finally {
                    // Reset the current check type.
                    checkTypeThreadLocal.set(currentCheckType);
                }
            }

            return doSecured(joinPoint, currentCheckType, (Secured) securityAnnotation);
        } catch (final Throwable t) {
            LOGGER.debug(joinPoint.getSignature().toString(), t);
            throw t;
        }
    }

    public Object doSecured(final ProceedingJoinPoint joinPoint, Boolean currentCheckType, final Secured securityAnnotation) throws Throwable {
        final String methodName = joinPoint.getSignature().toString();
        final String permission = securityAnnotation.value();

        Object returnObj = null;
        try {
            // We must be logged in to access a secured service.
            if (!securityContext.isLoggedIn()) {
                throw PermissionException.createLoginRequiredException(permission, methodName);
            }

            if (StringUtils.hasText(permission)) {
                checkAppPermission(permission, methodName);
            }

            // Reset the current check type.
            checkTypeThreadLocal.set(currentCheckType);

            returnObj = joinPoint.proceed();
        } finally {
            // Reset the current check type.
            checkTypeThreadLocal.set(currentCheckType);
        }

        return returnObj;
    }

    @SuppressWarnings("unchecked")
    private Annotation getSecurityAnnotation(final ProceedingJoinPoint joinPoint) {
        Annotation overridingAnnotation = null;
        boolean annotationOnMethod = false;

        for (final Class<?> clazz : SECURITY_ANNOTATIONS) {
            final Class<Annotation> annotationClass = (Class<Annotation>) clazz;
            Annotation annotation = null;

            // Try and get an annotation from the method.
            try {
                final Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
                annotation = method.getAnnotation(annotationClass);
                if (annotation == null) {
                    // If we didn't get the annotation from the join point
                    // method then try the method on the target class.
                    final Class<?> targetClass = joinPoint.getTarget().getClass();
                    final Method targetMethod = targetClass.getMethod(method.getName(), method.getParameterTypes());
                    annotation = targetMethod.getAnnotation(annotationClass);
                }

                // Method annotations override class level annotations so make a
                // note that the annotation was found on a method.
                if (annotation != null) {
                    annotationOnMethod = true;
                }
            } catch (final NoSuchMethodException e) {
                LOGGER.error(e.getMessage(), e);
            }

            if (annotation == null && !annotationOnMethod) {
                // If we didn't get one from the method them try the class.
                final Class<?> targetClass = joinPoint.getTarget().getClass();
                annotation = targetClass.getAnnotation(annotationClass);
            }

            if (annotation != null) {
                overridingAnnotation = annotation;
            }
        }

        return overridingAnnotation;
    }

    private void checkAppPermission(final String permission, final String methodName) {
        if (!securityContext.hasAppPermission(permission)) {
            throw PermissionException.createAppPermissionRequiredException(permission, methodName);
        }
    }
}
